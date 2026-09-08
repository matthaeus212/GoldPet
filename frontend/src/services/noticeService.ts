import { typedClient } from './api/typedClient';
import type { AppNoticeResponse } from '../types/api';

// Generated alias (Phase 4 sweep).
export type { AppNoticeResponse };

export const noticeService = {
  getActiveNotices: async (screen?: string): Promise<AppNoticeResponse[]> => {
    const params = screen ? { screen } : {};
    const response = await typedClient.get('/api/v1/app/notices', { params });
    return response.data;
  },

  getNoticesByType: async (type: string): Promise<AppNoticeResponse[]> => {
    const response = await typedClient.get('/api/v1/app/notices', { params: { type } });
    return response.data;
  },

  getMaintenanceNotice: async (): Promise<AppNoticeResponse | null> => {
    try {
      const response = await typedClient.get('/api/v1/app/notices/maintenance');
      return response.data;
    } catch {
      return null;
    }
  },
};
