// Auth Service - API integration for Authentication

import apiClient from "./api/client";
import { typedClient } from "./api/typedClient";
import { nativeBridge } from "../bridge/nativeBridge";
import { useAuthStore } from "../stores/authStore";
import type {
  AuthResponse,
  LoginRequest,
  SignupRequest,
  SnsSignupRequest,
} from "../types/api";

// Re-export for existing importers during migration window.
export type {
  AuthResponse,
  LoginRequest,
  SignupRequest,
  SnsSignupRequest,
  LinkSuggestionInfo,
} from "../types/api";

export const authService = {
  signup: async (data: SignupRequest): Promise<AuthResponse> => {
    // Preserve axios error shape so UI can read error.response.data.message / status
    const response = await typedClient.post("/api/v1/auth/signup", data);
    return response.data as AuthResponse;
  },

  snsSignup: async (data: SnsSignupRequest): Promise<AuthResponse> => {
    try {
      const response = await apiClient.post<AuthResponse>("/auth/sns-signup", data);
      return response.data;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      throw new Error(
        err.response?.data?.message || "SNS 회원가입에 실패했습니다."
      );
    }
  },

  login: async (data: LoginRequest): Promise<AuthResponse> => {
    try {
      const response = await typedClient.post("/api/v1/auth/login", data);
      return response.data as AuthResponse;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      throw new Error(
        err.response?.data?.message || "로그인에 실패했습니다."
      );
    }
  },

  checkUsername: async (username: string): Promise<{ available: boolean }> => {
    try {
      const response = await typedClient.post("/api/v1/auth/check-username", {
        username,
      });
      return response.data as { available: boolean };
    } catch {
      return { available: false };
    }
  },

  checkEmail: async (email: string): Promise<{ available: boolean; provider?: string }> => {
    try {
      const response = await typedClient.post("/api/v1/auth/check-email", { email });
      return response.data as { available: boolean; provider?: string };
    } catch {
      return { available: false };
    }
  },

  checkNickname: async (nickname: string): Promise<{ available: boolean }> => {
    try {
      const response = await typedClient.post("/api/v1/auth/check-nickname", {
        nickname,
      });
      return response.data as { available: boolean };
    } catch {
      return { available: false };
    }
  },

  /**
   * 이 IP 에서 dev-login 이 가능한지 서버에 묻는다.
   *
   * 예전에는 로그인 화면의 개발자 패널이 `import.meta.env.MODE === 'dev'` 로 렌더됐는데,
   * 라이브 프론트가 `vite build --mode dev` 로 빌드되므로 **모든 사용자에게 그 입력칸이 보였다**.
   * 거기에 남의 이메일을 넣으면 그 계정으로 로그인되던 상태였다(계정탈취). 이제 서버가
   * IP 허용목록으로 판정한 결과에만 의존한다 — 허용 IP 가 아니면 패널 자체가 렌더되지 않는다.
   */
  isDevLoginAvailable: async (): Promise<boolean> => {
    try {
      const res = await apiClient.get('/auth/dev-login/available');
      return res.data?.available === true;
    } catch {
      return false;   // 판정 불가 = 노출하지 않는다
    }
  },

  openLoginPopup: (provider: string, email?: string): Promise<AuthResponse> => {
    return new Promise((resolve, reject) => {
      // If email is provided, forcibly use test login (Dev Login).
      // DevAuthController is @OpenApiInternal so the spec omits it; stays on apiClient.
      if (email) {
          apiClient
            .post("/auth/dev-login", { email })
            .then((response) => {
              resolve(response.data);
            })
            .catch((err) => {
              console.error("Dev login failed", err);
              reject(err);
            });
          return;
      }

      // Native app: use Flutter bridge for social login
      if (nativeBridge.isAvailable()) {
        nativeBridge
          .callMethod("login", { provider })
          .then((raw) => {
            const result = raw as Record<string, unknown>;
            if (result.type === 'SIGNUP_REQUIRED') {
              const err = Object.assign(new Error('SIGNUP_REQUIRED'), {
                signupRequired: true,
                accessToken: result.accessToken,
                refreshToken: result.refreshToken,
                user: result.user,
                provider: result.provider,
              });
              reject(err);
            } else if (result.accessToken) {
              resolve(result as unknown as AuthResponse);
            } else if (result.success === false) {
              reject(new Error((result.message as string) || "로그인에 실패했습니다."));
            } else {
              resolve(result as unknown as AuthResponse);
            }
          })
          .catch((err: Error) => {
            reject(new Error(err.message || "로그인에 실패했습니다."));
          });
        return;
      }

      // Web browser: OAuth2 popup flow
      const width = 500;
      const height = 600;
      const left = window.screenX + (window.outerWidth - width) / 2;
      const top = window.screenY + (window.outerHeight - height) / 2;

      const popup = window.open(
        `${
          import.meta.env.VITE_API_BASE_URL || "http://localhost:8081"
        }/oauth2/authorization/${provider}`,
        "Login",
        `width=${width},height=${height},left=${left},top=${top}`
      );

      // Listen for message from popup
      const handleMessage = (event: MessageEvent) => {
        if (event.origin !== window.location.origin) return;
        if (event.data.type === "LOGIN_SUCCESS") {
          window.removeEventListener("message", handleMessage);
          resolve(event.data);
          popup?.close();
        } else if (event.data.type === "LOGIN_ERROR") {
          window.removeEventListener("message", handleMessage);
          reject(new Error(event.data.message || "로그인에 실패했습니다."));
          popup?.close();
        }
      };

      window.addEventListener("message", handleMessage);

      // Check if popup is closed without completing login
      const checkClosed = setInterval(() => {
        if (popup?.closed) {
          clearInterval(checkClosed);
          window.removeEventListener("message", handleMessage);
          reject(new Error("로그인이 취소되었습니다."));
        }
      }, 500);
    });
  },

  confirmLink: async (tempToken: string): Promise<AuthResponse> => {
    try {
      const response = await typedClient.post("/api/v1/auth/confirm-link", { tempToken });
      return response.data as AuthResponse;
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      throw new Error(
        err.response?.data?.message || "계정 연동에 실패했습니다."
      );
    }
  },

  refreshToken: async (): Promise<AuthResponse> => {
    const refreshToken = useAuthStore.getState().refreshToken;
    if (!refreshToken) {
      throw new Error("No refresh token");
    }

    try {
      const response = await typedClient.post("/api/v1/auth/refresh", { refreshToken });
      return response.data as AuthResponse;
    } catch {
      useAuthStore.getState().logout();
      throw new Error("세션이 만료되었습니다. 다시 로그인해주세요.");
    }
  },

  /**
   * 휴면 계정 해제 (STYLE-001).
   * 휴면 사용자는 access token 이 없으므로, 로그인 시도에서 자격증명이 검증된 직후 서버가
   * 내려준 단기 activationToken 으로 본인을 증명한다.
   */
  activateDormantAccount: async (activationToken: string): Promise<void> => {
    await apiClient.post('/api/v1/auth/dormant/activate', { activationToken });
  },
};
