import { typedClient } from './typedClient';
import type {
  DashboardStatsResponse,
  DashboardChartsResponse,
  RecentActivityItem,
} from '../types/api';

// Generated aliases (Phase 3.11.4).
export type DashboardStats = DashboardStatsResponse;
export type DashboardCharts = DashboardChartsResponse;
export type RecentActivity = RecentActivityItem;

// Manual: not in spec (chart series points are inline number/date pairs).
export interface ChartDataPoint {
  date: string;
  value: number;
}

export const dashboardService = {
  async getStats(): Promise<DashboardStats> {
    const response = await typedClient.get('/api/v1/admin/dashboard/stats');
    return response.data as DashboardStats;
  },

  async getCharts(days = 30): Promise<DashboardCharts> {
    const response = await typedClient.get('/api/v1/admin/dashboard/charts', {
      params: { days },
    });
    return response.data as DashboardCharts;
  },

  async getRecentActivity(limit = 10): Promise<RecentActivity[]> {
    const response = await typedClient.get('/api/v1/admin/dashboard/recent-activity', {
      params: { limit },
    });
    return response.data as RecentActivity[];
  },
};
