import { logger } from '../../utils/logger';
import { useEffect } from 'react';
import { nativeBridge } from '../../bridge/nativeBridge';
import { useAuthStore } from '../../stores/authStore';

/**
 * Flutter에서 인증 관련 이벤트를 수신하여 React 인증 상태를 동기화하는 컴포넌트.
 * - authExpired: 401 refresh 실패 시 로그아웃 + 로그인 페이지 이동
 * - tokenSync: iOS WKWebView content process kill 후 페이지 리로드 시
 *   Flutter secure storage의 토큰을 React Zustand에 동기화하여 401 방지
 */
export function AuthExpiredHandler() {
  useEffect(() => {
    const unsubAuthExpired = nativeBridge.onEvent('authExpired', () => {
      console.warn('[AuthExpiry] Received authExpired event from Flutter, logging out');
      useAuthStore.getState().logout();
      window.location.href = '/login';
    });

    const unsubTokenSync = nativeBridge.onEvent('tokenSync', (raw) => {
      const data = raw as { accessToken: string; refreshToken: string };
      const { accessToken, refreshToken } = data;
      if (!accessToken) return;
      const currentToken = useAuthStore.getState().token;
      // 이미 동일한 토큰이면 무시 (불필요한 상태 업데이트 방지)
      if (currentToken === accessToken) return;
      logger.debug('[TokenSync] Received token sync from Flutter, updating auth store');
      useAuthStore.getState().setTokens(accessToken, refreshToken);
    });

    return () => {
      unsubAuthExpired();
      unsubTokenSync();
    };
  }, []);

  return null;
}
