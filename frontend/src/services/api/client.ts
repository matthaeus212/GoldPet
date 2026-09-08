/**
 * API 클라이언트 설정
 */

import axios from 'axios';
import { useAuthStore } from '../../stores/authStore';

/** 휴면 안내 화면이 쓰는 서버 제공 정보 (STYLE-001). */
export interface DormantDetails {
  /** 로그인 시 자격증명이 검증된 직후 발급되는 단기 해제 토큰. */
  activationToken: string;
  lastLoginAt?: string;
  dormantAt?: string;
}

export const DORMANT_DETAILS_KEY = 'goldpet.dormant.details';


const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';

export const apiClient = axios.create({
  baseURL: `${API_BASE_URL}/api/v1`,
  headers: {
    'Content-Type': 'application/json',
    'ngrok-skip-browser-warning': 'true',
  },
});

// 요청 인터셉터: 토큰 + 기기 ID 추가
apiClient.interceptors.request.use((config) => {
  const { token, deviceId } = useAuthStore.getState();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (deviceId) {
    config.headers['X-Device-Id'] = deviceId;
  }
  return config;
});

// 토큰 갱신 상태 관리
let isRefreshing = false;
let failedQueue: Array<{ resolve: (value: unknown) => void; reject: (reason: unknown) => void }> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// 응답 인터셉터: 에러 처리
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = useAuthStore.getState().refreshToken;

      if (!refreshToken) {
        useAuthStore.getState().logout();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      if (isRefreshing) {
        // 이미 갱신 중이면 큐에 추가
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then(token => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        }).catch(err => {
          return Promise.reject(err);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const deviceId = useAuthStore.getState().deviceId;
        const refreshHeaders: Record<string, string> = { 'Content-Type': 'application/json' };
        if (deviceId) refreshHeaders['X-Device-Id'] = deviceId;
        const response = await axios.post(
          `${API_BASE_URL}/api/v1/auth/refresh`,
          { refreshToken },
          { headers: refreshHeaders }
        );

        const { accessToken: newToken, refreshToken: newRefreshToken } = response.data;
        useAuthStore.getState().setTokens(newToken, newRefreshToken);

        processQueue(null, newToken);
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().logout();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    } else if (error.response?.status === 403) {
      const errorCode = error.response?.data?.errorCode;
      if (errorCode === 'DORMANT_ACCOUNT') {
        // STYLE-001: 서버가 details 로 해제 토큰과 실제 날짜(마지막 접속·휴면 전환)를 내려준다.
        // 예전에는 이걸 버리고 화면이 'OOO' 와 가짜 날짜를 하드코딩하고 있었다.
        const details = error.response?.data?.details as DormantDetails | undefined;
        if (details?.activationToken) {
          sessionStorage.setItem(DORMANT_DETAILS_KEY, JSON.stringify(details));
        }
        useAuthStore.getState().logout();
        window.location.href = '/account/dormant';
      } else if (errorCode === 'WITHDRAWN_ACCOUNT') {
        useAuthStore.getState().logout();
        window.location.href = '/account/withdrawn';
      } else if (errorCode === 'SUSPENDED_ACCOUNT') {
        useAuthStore.getState().logout();
        window.location.href = '/account/suspended';
      }
      // Unknown 403 errorCodes (e.g. ACCESS_DENIED for ownership checks) intentionally
      // fall through to the Promise.reject(error) below — that is the correct default.
    } else if (error.response?.status >= 500) {
      // Show user-friendly error for server errors
      window.dispatchEvent(new CustomEvent('gp-show-toast', {
        detail: { message: '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.', type: 'error' }
      }));
    }

    const status = error.response?.status;
    if (status === 400) {
      const errorCode = error.response?.data?.errorCode;
      if (errorCode === 'VALIDATION_ERROR') {
        error.validationDetails = error.response?.data?.details || {};
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
