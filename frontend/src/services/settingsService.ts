import { typedClient } from './api/typedClient';

export interface NotificationSettings {
  pushAlert: boolean;
  chatAlert: boolean;
  communityAlert: boolean;
  marketingAlert: boolean;
  reengagementAlert: boolean; // W2c: 산책 리마인더 / 재참여 알림 (backend: isReengagementAlertEnabled)
}

export interface PrivacySettings {
  locationSharing: boolean;
  profilePublic: boolean;
}

export const settingsService = {
  getNotificationSettings: async (): Promise<NotificationSettings> => {
    const response = await typedClient.get('/api/v1/users/me/notification-settings');
    return response.data as NotificationSettings;
  },

  updateNotificationSettings: async (settings: NotificationSettings): Promise<NotificationSettings> => {
    const response = await typedClient.put('/api/v1/users/me/notification-settings', settings);
    return response.data as NotificationSettings;
  },

  getPrivacySettings: async (): Promise<PrivacySettings> => {
    const response = await typedClient.get('/api/v1/users/me/privacy-settings');
    const data = response.data as { isLocationSharingEnabled: boolean; isProfilePublic: boolean };
    return {
      locationSharing: data.isLocationSharingEnabled,
      profilePublic: data.isProfilePublic,
    };
  },

  updatePrivacySettings: async (settings: PrivacySettings): Promise<PrivacySettings> => {
    await typedClient.put('/api/v1/users/me/privacy-settings', {
      isLocationSharingEnabled: settings.locationSharing,
      isProfilePublic: settings.profilePublic,
    });
    return settings;
  },

  exportMyData: async (): Promise<void> => {
    try {
      const response = await typedClient.get('/api/v1/users/me/data-export');
      const blob = new Blob([JSON.stringify(response.data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'goldpet-data-export.json';
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      throw new Error('데이터 다운로드에 실패했습니다.');
    }
  },
};
