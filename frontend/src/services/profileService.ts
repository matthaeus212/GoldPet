// Profile & Settings Service

import { typedClient } from './api/typedClient';
import { useAuthStore } from '../stores/authStore';

export const profileService = {
  logout: async (): Promise<void> => {
    try {
      await typedClient.post('/api/v1/auth/logout', undefined as never);
    } catch {
      // Ignore logout API errors
    }
    useAuthStore.getState().logout();
  },

  deleteAccount: async (): Promise<void> => {
    try {
      await typedClient.delete('/api/v1/users/me');
      useAuthStore.getState().logout();
    } catch {
      throw new Error('회원 탈퇴에 실패했습니다.');
    }
  },
};
