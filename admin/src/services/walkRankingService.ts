import { typedClient } from './typedClient';
import type {
  WalkRankingAdminResponse,
  BestWalkCoupleResponse,
  BestWalkCoupleRequest,
} from '../types/api';

// Re-export under historical local names (Phase 3.9).
export type WalkRankingAdminEntry = WalkRankingAdminResponse;
export type { BestWalkCoupleResponse };
export type SetBestCoupleRequest = BestWalkCoupleRequest;

export const walkRankingService = {
  async getRanking(yearMonth: string, size = 50): Promise<WalkRankingAdminEntry[]> {
    const response = await typedClient.get('/api/v1/admin/walks/ranking', {
      params: { yearMonth, size },
    });
    return response.data as WalkRankingAdminEntry[];
  },

  async getBestCouple(yearMonth: string): Promise<BestWalkCoupleResponse | null> {
    try {
      const response = await typedClient.get('/api/v1/admin/walks/best-couple', {
        params: { yearMonth },
      });
      return response.data as BestWalkCoupleResponse;
    } catch (err: unknown) {
      const e = err as { response?: { status?: number } };
      if (e.response?.status === 404) return null;
      throw err;
    }
  },

  async setBestCouple(request: SetBestCoupleRequest): Promise<BestWalkCoupleResponse> {
    const response = await typedClient.post('/api/v1/admin/walks/best-couple', request);
    return response.data as BestWalkCoupleResponse;
  },

  async deleteBestCouple(yearMonth: string): Promise<void> {
    await typedClient.deletePath(
      '/api/v1/admin/walks/best-couple/{yearMonth}',
      { yearMonth },
    );
  },
};
