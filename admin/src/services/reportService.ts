import { typedClient } from './typedClient';
import type { PageResponse, ReportAdminResponse } from '../types/api';

// Generated alias (Phase 3.11.2).
export type ReportItem = ReportAdminResponse;

export const reportService = {
  async getReports(params: { page?: number; status?: string }): Promise<PageResponse<ReportItem>> {
    const response = await typedClient.get('/api/v1/admin/reports', { params });
    return response.data as unknown as PageResponse<ReportItem>;
  },

  async getReportDetail(reportId: number): Promise<ReportItem> {
    const response = await typedClient.getPath(
      '/api/v1/admin/reports/{reportId}',
      { reportId },
    );
    return response.data as ReportItem;
  },

  async resolveReport(reportId: number, actionType: string, adminNote?: string): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/reports/{reportId}/resolve',
      { reportId },
      { actionType, adminNote } as unknown as Parameters<typeof typedClient.postPath<'/api/v1/admin/reports/{reportId}/resolve'>>[2],
    );
  },

  async dismissReport(reportId: number): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/reports/{reportId}/dismiss',
      { reportId },
      undefined,
    );
  },
};
