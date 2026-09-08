import { typedClient } from './api/typedClient';
import type { ReportType, CreateReportRequest } from '../types/api';

export type { ReportType, CreateReportRequest };

export const reportService = {
  createReport: async (request: CreateReportRequest) => {
    return typedClient.post('/api/v1/reports', request);
  },
};
