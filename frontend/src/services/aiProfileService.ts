import { typedClient } from './api/typedClient';
import type {
  AIStyleOption as AIStyleOptionDto,
  CreateAIProfileRequest as CreateAIProfileRequestDto,
  AIProfileRequestResponse as AIProfileRequestResponseDto,
  AIRequestType as AIRequestTypeAlias,
  ApplyProfileRequest,
} from '../types/api';

// Generated aliases (Phase 3.10).
export type AIStyleOption = AIStyleOptionDto;
export type AIRequestType = AIRequestTypeAlias;
export type CreateAIProfileRequest = CreateAIProfileRequestDto;
export type AIProfileRequestResponse = AIProfileRequestResponseDto;
// Backend type is `ApplyProfileRequest`; legacy frontend name kept.
export type ApplyToProfileRequest = ApplyProfileRequest;

class AIProfileService {
  async getStyles(): Promise<AIStyleOption[]> {
    const response = await typedClient.get('/api/v1/ai-profile/styles');
    return response.data as AIStyleOption[];
  }

  async createRequest(request: CreateAIProfileRequest): Promise<AIProfileRequestResponse> {
    const response = await typedClient.post('/api/v1/ai-profile/requests', request);
    return response.data as AIProfileRequestResponse;
  }

  async getMyRequests(page: number): Promise<AIProfileRequestResponse[]> {
    const response = await typedClient.get('/api/v1/ai-profile/requests', {
      params: { page },
    });
    return response.data as unknown as AIProfileRequestResponse[];
  }

  async getRequest(requestId: number): Promise<AIProfileRequestResponse> {
    const response = await typedClient.getPath(
      '/api/v1/ai-profile/requests/{requestId}',
      { requestId },
    );
    return response.data as AIProfileRequestResponse;
  }

  async getPendingCount(): Promise<number> {
    const response = await typedClient.get('/api/v1/ai-profile/pending-count');
    return (response.data as { count: number }).count;
  }

  async cancelRequest(requestId: number): Promise<void> {
    await typedClient.deletePath(
      '/api/v1/ai-profile/requests/{requestId}',
      { requestId },
    );
  }

  async applyToProfile(requestId: number, data: ApplyToProfileRequest): Promise<void> {
    await typedClient.postPath(
      '/api/v1/ai-profile/requests/{requestId}/apply',
      { requestId },
      data,
    );
  }

  async getLoadingTips(): Promise<string[]> {
    const response = await typedClient.get('/api/v1/ai-profile/loading-tips');
    const tips = response.data as { id: number; content: string; displayOrder: number; isActive: boolean }[];
    return tips.map((tip) => tip.content);
  }
}

export const aiProfileService = new AIProfileService();
