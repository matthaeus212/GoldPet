import { typedClient } from './typedClient';
import type { BadgeResponse } from '../types/api';

// Generated alias (Phase 3.11.1). Admin BadgeResponse is missing `isActive` /
// `earnedCount` in the spec even though the backend returns isActive at runtime
// (see GamificationPage usage). Widen the alias locally until the backend DTO
// adds the field so call sites compile.
export type Badge = BadgeResponse & { isActive?: boolean; earnedCount?: number };

export const gamificationService = {
  async getBadges(): Promise<Badge[]> {
    const response = await typedClient.get('/api/v1/admin/gamification/badges');
    return response.data as Badge[];
  },

  async createBadge(data: {
    name: string;
    description: string;
    imageUrl?: string;
    conditionType?: string;
    conditionValue?: number;
    rewardGold?: number;
    startDate?: string;
    endDate?: string;
    isRepeatable?: boolean;
    repeatCycle?: string;
  }): Promise<Badge> {
    const body = data as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/gamification/badges'>>[1];
    const response = await typedClient.post('/api/v1/admin/gamification/badges', body);
    return response.data as Badge;
  },

  async updateBadge(badgeId: number, data: {
    name?: string;
    description?: string;
    imageUrl?: string;
    conditionType?: string;
    conditionValue?: number;
    isActive?: boolean;
    rewardGold?: number;
    startDate?: string;
    endDate?: string;
    isRepeatable?: boolean;
    repeatCycle?: string;
  }): Promise<Badge> {
    const body = data as unknown as Parameters<typeof typedClient.putPath<'/api/v1/admin/gamification/badges/{badgeId}'>>[2];
    const response = await typedClient.putPath(
      '/api/v1/admin/gamification/badges/{badgeId}',
      { badgeId },
      body,
    );
    return response.data as Badge;
  },

  async deleteBadge(id: number): Promise<void> {
    await typedClient.deletePath('/api/v1/admin/gamification/badges/{badgeId}', { badgeId: id });
  },
};
