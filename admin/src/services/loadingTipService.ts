import { typedClient } from './typedClient';
import type {
  AILoadingTipResponse,
  CreateLoadingTipRequest,
  UpdateLoadingTipRequest,
} from '../types/api';

// Re-export under historical local names (Phase 3.11.1).
export type {
  AILoadingTipResponse,
  CreateLoadingTipRequest,
  UpdateLoadingTipRequest,
};

export const loadingTipService = {
  getAllTips: async (): Promise<AILoadingTipResponse[]> => {
    const response = await typedClient.get('/api/v1/admin/ai-profile/loading-tips');
    return response.data as AILoadingTipResponse[];
  },
  createTip: async (request: CreateLoadingTipRequest): Promise<AILoadingTipResponse> => {
    const response = await typedClient.post('/api/v1/admin/ai-profile/loading-tips', request);
    return response.data as AILoadingTipResponse;
  },
  updateTip: async (id: number, request: UpdateLoadingTipRequest): Promise<AILoadingTipResponse> => {
    const response = await typedClient.putPath(
      '/api/v1/admin/ai-profile/loading-tips/{tipId}',
      { tipId: id },
      request,
    );
    return response.data as AILoadingTipResponse;
  },
  deleteTip: async (id: number): Promise<void> => {
    await typedClient.deletePath(
      '/api/v1/admin/ai-profile/loading-tips/{tipId}',
      { tipId: id },
    );
  },
};
