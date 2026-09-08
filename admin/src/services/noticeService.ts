import { apiClient } from './apiClient';
import { typedClient } from './typedClient';
import type {
  AppNoticeResponse,
  CreateAppNoticeRequest,
  UpdateAppNoticeRequest,
} from '../types/api';

// Re-export under historical local names (Phase 3.11.4).
export type { AppNoticeResponse, CreateAppNoticeRequest, UpdateAppNoticeRequest };

export const noticeService = {
  getAllNotices: async (): Promise<AppNoticeResponse[]> => {
    const response = await typedClient.get('/api/v1/admin/notices');
    return response.data as AppNoticeResponse[];
  },
  createNotice: async (request: CreateAppNoticeRequest): Promise<AppNoticeResponse> => {
    const response = await typedClient.post('/api/v1/admin/notices', request);
    return response.data as AppNoticeResponse;
  },
  updateNotice: async (id: number, request: UpdateAppNoticeRequest): Promise<AppNoticeResponse> => {
    const response = await typedClient.putPath(
      '/api/v1/admin/notices/{id}',
      { id },
      request,
    );
    return response.data as AppNoticeResponse;
  },
  deleteNotice: async (id: number): Promise<void> => {
    await typedClient.deletePath('/api/v1/admin/notices/{id}', { id });
  },

  uploadImage: async (file: File): Promise<string> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('category', 'notice');
    // Multipart — stays on apiClient; typedClient is JSON-only.
    const response = await apiClient.post('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data.url;
  },
};
