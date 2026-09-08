import { typedClient } from './typedClient';
import type {
  PageResponse,
  AIRequestAdminResponse,
  AIStyleAdminResponse,
  AIStyleAdminRequest,
  AIProfileStatsResponse,
} from '../types/api';

// Re-export under historical local names (Phase 3.11.1).
export type {
  AIRequestAdminResponse,
  AIStyleAdminResponse,
  AIStyleAdminRequest,
  AIProfileStatsResponse,
};

export const aiProfileAdminService = {
  async getRequests(page: number = 0, size: number = 20, status?: string): Promise<PageResponse<AIRequestAdminResponse>> {
    const params: Record<string, string | number> = { page, size };
    if (status) {
      params.status = status;
    }
    const response = await typedClient.get('/api/v1/admin/ai-profile/requests', { params });
    return response.data as unknown as PageResponse<AIRequestAdminResponse>;
  },

  async getRequestDetail(id: number): Promise<AIRequestAdminResponse> {
    const response = await typedClient.getPath(
      '/api/v1/admin/ai-profile/requests/{requestId}',
      { requestId: id },
    );
    return response.data as AIRequestAdminResponse;
  },

  async retryRequest(id: number): Promise<AIRequestAdminResponse> {
    const response = await typedClient.postPath(
      '/api/v1/admin/ai-profile/requests/{requestId}/retry',
      { requestId: id },
      undefined,
    );
    return response.data as AIRequestAdminResponse;
  },

  async refundRequest(id: number): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/ai-profile/requests/{requestId}/refund',
      { requestId: id },
      undefined,
    );
  },

  async forceFailRequest(id: number): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/ai-profile/requests/{requestId}/force-fail',
      { requestId: id },
      undefined,
    );
  },

  async getStyles(): Promise<AIStyleAdminResponse[]> {
    const response = await typedClient.get('/api/v1/admin/ai-profile/styles');
    return response.data as AIStyleAdminResponse[];
  },

  async createStyle(data: AIStyleAdminRequest): Promise<AIStyleAdminResponse> {
    const response = await typedClient.post('/api/v1/admin/ai-profile/styles', data);
    return response.data as AIStyleAdminResponse;
  },

  async updateStyle(id: string, data: AIStyleAdminRequest): Promise<AIStyleAdminResponse> {
    const response = await typedClient.putPath(
      '/api/v1/admin/ai-profile/styles/{styleId}',
      { styleId: id },
      data,
    );
    return response.data as AIStyleAdminResponse;
  },

  async deleteStyle(id: string): Promise<void> {
    await typedClient.deletePath(
      '/api/v1/admin/ai-profile/styles/{styleId}',
      { styleId: id },
    );
  },

  async toggleStyle(id: string): Promise<AIStyleAdminResponse> {
    const response = await typedClient.patchPath(
      '/api/v1/admin/ai-profile/styles/{styleId}/toggle',
      { styleId: id },
      undefined,
    );
    return response.data as AIStyleAdminResponse;
  },

  async getStats(): Promise<AIProfileStatsResponse> {
    const response = await typedClient.get('/api/v1/admin/ai-profile/stats');
    return response.data as AIProfileStatsResponse;
  },
};
