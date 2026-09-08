import apiClient from './api/client';

export interface StreakResponse {
  currentStreak: number;
  longestStreak: number;
  lastActiveDate: string | null; // "YYYY-MM-DD" KST, null = cold-start
  freezeCount: number;           // weekly free freeze remaining
  activeToday: boolean;          // walked today (KST)
}

export const streakService = {
  // Uses apiClient (not typedClient) — /api/v1/streaks/me is not yet in the
  // generated OpenAPI schema. apiClient.baseURL is `${VITE_API_BASE_URL}/api/v1`.
  getMyStreak: async (): Promise<StreakResponse> => {
    const response = await apiClient.get<StreakResponse>('/streaks/me');
    return response.data;
  },
};
