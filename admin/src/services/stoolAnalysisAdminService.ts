import { typedClient } from './typedClient';
import type {
  PageResponse,
  StoolAnalysisStatsResponse,
  DailyCount,
  AdminStoolAnalysisItem,
} from '../types/api';

// Generated aliases (Phase 3.11.4).
export type StoolAnalysisStats = StoolAnalysisStatsResponse;
export type { DailyCount, AdminStoolAnalysisItem };

export const stoolAnalysisAdminService = {
  async getStats(): Promise<StoolAnalysisStats> {
    const response = await typedClient.get('/api/v1/admin/stool-analyses/stats');
    return response.data as StoolAnalysisStats;
  },

  async getAnalyses(params: { page?: number; status?: string }): Promise<PageResponse<AdminStoolAnalysisItem>> {
    const response = await typedClient.get('/api/v1/admin/stool-analyses', { params });
    return response.data as unknown as PageResponse<AdminStoolAnalysisItem>;
  },

  async getDailyLimit(): Promise<number> {
    const response = await typedClient.get('/api/v1/admin/system/configs');
    const configs = response.data as unknown as { key: string; value: string }[];
    const setting = configs.find((c) => c.key === 'stool.analysis.daily_limit');
    return setting ? parseInt(setting.value, 10) : 10;
  },

  async updateDailyLimit(value: number): Promise<void> {
    await typedClient.post('/api/v1/admin/system/configs', {
      key: 'stool.analysis.daily_limit',
      value: value.toString(),
    });
  },
};
