import { typedClient } from './typedClient';

interface NotificationTemplate {
  id: number;
  title: string;
  body: string;
  category: string;
  createdAt: string;
}

interface NotificationTemplateRequest {
  title: string;
  body: string;
  category: string;
}

interface SendNotificationRequest {
  templateId: number | null;
  title: string | null;
  body: string | null;
  targetType: 'ALL' | 'USER';
  targetUserIds: number[] | null;
}

interface SendNotificationResult {
  successCount: number;
  failCount: number;
}

interface DeliveryLog {
  id: number;
  title: string;
  targetType: string;
  sentCount: number;
  sentAt: string;
}

export const marketingService = {
  getTemplates: async (): Promise<NotificationTemplate[]> => {
    const response = await typedClient.get('/api/v1/admin/marketing/notifications/templates');
    return response.data as unknown as NotificationTemplate[];
  },

  createTemplate: async (request: NotificationTemplateRequest): Promise<NotificationTemplate> => {
    const body = request as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/marketing/notifications/templates'>>[1];
    const response = await typedClient.post('/api/v1/admin/marketing/notifications/templates', body);
    return response.data as unknown as NotificationTemplate;
  },

  updateTemplate: async (id: number, request: NotificationTemplateRequest): Promise<NotificationTemplate> => {
    const body = request as unknown as Parameters<typeof typedClient.putPath<'/api/v1/admin/marketing/notifications/templates/{id}'>>[2];
    const response = await typedClient.putPath(
      '/api/v1/admin/marketing/notifications/templates/{id}',
      { id },
      body,
    );
    return response.data as unknown as NotificationTemplate;
  },

  deleteTemplate: async (id: number): Promise<void> => {
    await typedClient.deletePath(
      '/api/v1/admin/marketing/notifications/templates/{id}',
      { id },
    );
  },

  sendNotification: async (request: SendNotificationRequest): Promise<SendNotificationResult> => {
    const body = request as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/marketing/notifications/send'>>[1];
    const response = await typedClient.post('/api/v1/admin/marketing/notifications/send', body);
    return response.data as unknown as SendNotificationResult;
  },

  getDeliveryLogs: async (page = 0, size = 20): Promise<DeliveryLog[]> => {
    const response = await typedClient.get('/api/v1/admin/marketing/notifications/logs', {
      params: { page, size },
    });
    return response.data as unknown as DeliveryLog[];
  },

  /* eslint-disable @typescript-eslint/no-explicit-any */
  getBanners: async (): Promise<any[]> => {
    const response = await typedClient.get('/api/v1/admin/marketing/banners');
    return response.data as unknown as any[];
  },
  /* eslint-enable @typescript-eslint/no-explicit-any */

  createBanner: async (request: Record<string, unknown>): Promise<Record<string, unknown>> => {
    const body = request as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/marketing/banners'>>[1];
    const response = await typedClient.post('/api/v1/admin/marketing/banners', body);
    return response.data as unknown as Record<string, unknown>;
  },

  updateBanner: async (id: number, request: Record<string, unknown>): Promise<Record<string, unknown>> => {
    const body = request as unknown as Parameters<typeof typedClient.putPath<'/api/v1/admin/marketing/banners/{bannerId}'>>[2];
    const response = await typedClient.putPath(
      '/api/v1/admin/marketing/banners/{bannerId}',
      { bannerId: id },
      body,
    );
    return response.data as unknown as Record<string, unknown>;
  },

  deleteBanner: async (id: number): Promise<void> => {
    await typedClient.deletePath(
      '/api/v1/admin/marketing/banners/{bannerId}',
      { bannerId: id },
    );
  },

  getCampaigns: async (): Promise<Record<string, unknown>[]> => {
    const response = await typedClient.get('/api/v1/admin/marketing/campaigns');
    return response.data as unknown as Record<string, unknown>[];
  },

  createCampaign: async (request: Record<string, unknown>): Promise<Record<string, unknown>> => {
    const body = request as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/marketing/campaigns'>>[1];
    const response = await typedClient.post('/api/v1/admin/marketing/campaigns', body);
    return response.data as unknown as Record<string, unknown>;
  },
};

export type {
  NotificationTemplate,
  NotificationTemplateRequest,
  SendNotificationRequest,
  SendNotificationResult,
  DeliveryLog,
};
