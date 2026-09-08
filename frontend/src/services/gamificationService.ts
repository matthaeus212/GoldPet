import { typedClient } from './api/typedClient';
import type { BadgeResponse } from '../types/api';

// Generated alias (Phase 3.10). Backend exposes nullable date/condition fields as
// `optional` — frontend consumers should treat absent and null interchangeably.
export type Badge = BadgeResponse;

export const gamificationService = {
  getAllBadges: async (): Promise<Badge[]> => {
    const response = await typedClient.get('/api/v1/gamification/badges');
    return response.data;
  },

  getMyBadges: async (): Promise<Badge[]> => {
    const response = await typedClient.get('/api/v1/gamification/badges/my');
    return response.data;
  },
};
