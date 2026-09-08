import { typedClient } from './api/typedClient';
import type {
  NotificationType,
  NotificationResponse,
  PageNotificationResponse,
  UnreadCountResponse,
} from '../types/api';

export type { NotificationType };
export type Notification = NotificationResponse;

export const notificationService = {
  getNotifications: async (
    category = 'ALL',
    page = 0,
    size = 20,
  ): Promise<{ notifications: Notification[]; hasMore: boolean }> => {
    try {
      const response = await typedClient.get('/api/v1/notifications', {
        params: { category, page, size },
      });
      const data = response.data as PageNotificationResponse;
      return {
        notifications: data.content ?? [],
        hasMore: !(data.last ?? true),
      };
    } catch {
      return { notifications: [], hasMore: false };
    }
  },

  getUnreadCount: async (): Promise<number> => {
    try {
      const response = await typedClient.get('/api/v1/notifications/unread-count');
      const data = response.data as UnreadCountResponse;
      return data.count ?? 0;
    } catch {
      return 0;
    }
  },

  markAsRead: async (notificationId: number): Promise<void> => {
    try {
      await typedClient.postPath(
        '/api/v1/notifications/{notificationId}/read',
        { notificationId },
        undefined,
      );
    } catch {
      // Ignore
    }
  },

  markAllAsRead: async (): Promise<void> => {
    try {
      await typedClient.post('/api/v1/notifications/read-all', undefined);
    } catch {
      // Ignore
    }
  },

  deleteNotification: async (notificationId: number): Promise<void> => {
    try {
      await typedClient.deletePath(
        '/api/v1/notifications/{notificationId}',
        { notificationId },
      );
    } catch {
      throw new Error('알림 삭제에 실패했습니다.');
    }
  },
};
