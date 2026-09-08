import { apiClient } from './apiClient';
import { typedClient } from './typedClient';
import type {
  PageResponse,
  WalkAdminResponse as WalkAdminResponseBase,
  WalkDetailResponse,
  WalkSpotAdminDto,
  WalkStatsDetailResponse,
} from '../types/api';

// Re-export OpenAPI-generated types (Phase 3.9). `WalkSpotDto` keeps its prior local
// name for callers; underlying shape now comes from `Schemas['WalkSpotAdminDto']`.
// WalkAdminResponse widened to carry `spotsCount` which the backend returns at
// runtime (LBSPage consumes it) but is not declared in the spec yet.
export type WalkAdminResponse = WalkAdminResponseBase & { spotsCount?: number };
export type { WalkDetailResponse, WalkStatsDetailResponse };
export type WalkSpotDto = WalkSpotAdminDto;

export interface GetWalksParams {
  page?: number;
  size?: number;
  userNickname?: string;
  startDate?: string;
  endDate?: string;
  minDistance?: number;
  maxDistance?: number;
}

export interface GetDetailedStatsParams {
  startDate?: string;
  endDate?: string;
}

export const walkAdminService = {
  async getWalks(params: GetWalksParams = {}): Promise<PageResponse<WalkAdminResponse>> {
    const response = await typedClient.get('/api/v1/admin/walks', { params });
    return response.data as unknown as PageResponse<WalkAdminResponse>;
  },

  async getWalkDetail(id: number): Promise<WalkDetailResponse> {
    const response = await typedClient.getPath(
      '/api/v1/admin/walks/{walkId}',
      { walkId: id },
    );
    return response.data as WalkDetailResponse;
  },

  async deleteWalk(id: number, reason: string): Promise<void> {
    // DELETE with JSON body — typedClient Config disallows `data`, so stay on apiClient.
    await apiClient.delete(`/walks/${id}`, { data: { reason } });
  },

  async getDetailedStats(params: GetDetailedStatsParams = {}): Promise<WalkStatsDetailResponse> {
    const response = await typedClient.get('/api/v1/admin/walks/stats/detailed', { params });
    return response.data as WalkStatsDetailResponse;
  },
};
