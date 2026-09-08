/**
 * 인증 상태 관리
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { nativeBridge } from '../bridge/nativeBridge';
import { queryClient } from '../lib/queryClient';
import { chatWebSocket } from '../services/websocket/chatWebSocket';

const DEVICE_ID_STORAGE_KEY = 'gp-device-id';

/** 스탠드얼론 브라우저(Flutter WebView 밖)용 영속 UUID 폴백 */
function getOrCreateLocalDeviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_STORAGE_KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(DEVICE_ID_STORAGE_KEY, id);
  }
  return id;
}

export interface User {
  id: number;
  nickname: string;
  email?: string | null;
  profileImageUrl?: string;
  profileImageUrls?: string[];
  profileImageUrlThumbnail?: string;
  profileImageUrlViewer?: string;
  profileImageUrlsThumbnail?: string[];
  profileImageUrlsViewer?: string[];
  gender?: string;
  birthYear?: number;
  mainLocationText?: string;
  mainLocationLat?: number;
  mainLocationLng?: number;
  goldBalance?: number;
  phoneNumber?: string | null;
  name?: string;
  birthDate?: string;
  mbti?: string;
  interests?: string[];
  hobbies?: string[];
  intro?: string;
  hasPet?: boolean;
  isNotificationEnabled?: boolean;
  isActive?: boolean;
  oauthProvider?: string;
  isProfileLocked?: boolean;
  // 온보딩/추가가입 완료 단일 기준. self(/users/me)에서만 실제 boolean.
  signupCompleted?: boolean;
}

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: User | null;
  isAuthenticated: boolean;
  deviceId: string | null;
  login: (token: string, refreshToken: string, user: User) => void;
  logout: () => void;
  updateUser: (user: User) => void;
  setTokens: (token: string, refreshToken: string) => void;
  setDeviceId: (deviceId: string) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      deviceId: null,
      login: (token, refreshToken, user) => {
        // 방어적: 이전 유저 캐시 제거
        queryClient.cancelQueries();
        queryClient.clear();
        set({ token, refreshToken, user, isAuthenticated: true });
        if (nativeBridge.isAvailable()) {
          // Sync auth tokens to Flutter secure storage for native API calls
          nativeBridge.callMethod('syncAuthToken', { accessToken: token, refreshToken }).catch((e) => console.warn('[syncAuthToken] failed:', e));
          // Register FCM token — capture deviceId from bridge response (primary mechanism)
          nativeBridge
            .callMethod('registerFcmToken', { accessToken: token })
            .then((res) => {
              const data = res as { deviceId?: string } | null;
              if (data?.deviceId) {
                set({ deviceId: data.deviceId });
              }
            })
            .catch(() => {});
        } else {
          // Standalone browser (not in Flutter WebView): stable localStorage UUID fallback
          set({ deviceId: getOrCreateLocalDeviceId() });
        }
      },
      logout: () => {
        // 1. WebSocket 해제 (토큰 참조하므로 먼저)
        chatWebSocket.disconnect();
        // 2-3. React Query 캐시 정리
        queryClient.cancelQueries();
        queryClient.clear();
        // 4. user-specific localStorage 삭제
        localStorage.removeItem('friendFindFilter');
        localStorage.removeItem('autoLogin');
        // 5. Flutter 토큰 삭제
        if (nativeBridge.isAvailable()) {
          nativeBridge.callMethod('clearAuthToken').catch(() => {});
        }
        // 6. Zustand 상태 초기화 (deviceId 유지 — 기기는 변하지 않음)
        set({ token: null, refreshToken: null, user: null, isAuthenticated: false });
      },
      updateUser: (user) => {
        set({ user });
      },
      setTokens: (token, refreshToken) => {
        set({ token, refreshToken });
        if (nativeBridge.isAvailable()) {
          nativeBridge.callMethod('syncAuthToken', { accessToken: token, refreshToken }).catch((e) => console.warn('[syncAuthToken] failed:', e));
        }
      },
      setDeviceId: (deviceId) => {
        set({ deviceId });
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        token: state.token,
        refreshToken: state.refreshToken,
        isAuthenticated: state.isAuthenticated,
        user: state.user,
        deviceId: state.deviceId,
      }),
    }
  )
);

// Secondary deviceId capture: listen for Flutter → React 'deviceRegistered' event.
// Fires after PUT /api/v1/users/me/devices succeeds on Flutter side; also buffered for
// cold-start (BUFFERABLE_EVENTS in nativeBridge.ts) so early events are not dropped.
nativeBridge.onEvent('deviceRegistered', (data) => {
  const d = data as { deviceId?: string } | null;
  if (d?.deviceId) {
    useAuthStore.getState().setDeviceId(d.deviceId);
  }
});

