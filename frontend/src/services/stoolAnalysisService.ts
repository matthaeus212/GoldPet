import { typedClient } from './api/typedClient';
import type {
  AnalysisStatus,
  StoolAnalysisResponse,
  HealthTrendResponse,
  MonthlyScore,
} from '../types/api';

// Generated re-exports (Phase 4 sweep — Phase 3.4 finished the enum migration but
// the DTO interfaces had been kept as duplicates here).
export type { AnalysisStatus, StoolAnalysisResponse, HealthTrendResponse, MonthlyScore };

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  last: boolean;
  first: boolean;
}

export const stoolAnalysisService = {
  getAnalysisHistory: async (petId: number, page: number): Promise<PageResponse<StoolAnalysisResponse>> => {
    const response = await typedClient.getPath(
      '/api/v1/health/stool-analyses/pet/{petId}',
      { petId },
      { params: { page, size: 20 } },
    );
    return response.data as PageResponse<StoolAnalysisResponse>;
  },

  getAnalysis: async (id: number): Promise<StoolAnalysisResponse> => {
    const response = await typedClient.getPath(
      '/api/v1/health/stool-analyses/{id}',
      { id },
    );
    return response.data;
  },

  getHealthTrend: async (petId: number, months = 6): Promise<HealthTrendResponse> => {
    const response = await typedClient.getPath(
      '/api/v1/health/stool-analyses/pet/{petId}/trend',
      { petId },
      { params: { months } },
    );
    return response.data;
  },
};
