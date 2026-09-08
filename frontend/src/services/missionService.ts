import apiClient from './api/client';

export interface DailyMission {
  badgeId: number;
  name: string;
  description: string;
  imageUrl: string | null;
  conditionType: 'WALK_DISTANCE_TOTAL' | 'COMMUNITY_POST' | 'CHECK_IN';
  conditionValue: number;
  rewardGold: number;
  currentValue: number | null;   // null = no activity yet today
  completed: boolean;
  completedAt: string | null;    // ISO timestamp, null if incomplete
}

export interface TodayMissionsResponse {
  missionDate: string;           // YYYY-MM-DD (KST)
  missions: DailyMission[];      // always 3 items
}

export const missionService = {
  // Uses apiClient — /api/v1/gamification/missions/today is not yet in the schema.
  getTodayMissions: async (): Promise<TodayMissionsResponse> => {
    const response = await apiClient.get<TodayMissionsResponse>('/gamification/missions/today');
    return response.data;
  },
};
