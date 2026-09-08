import { typedClient } from './typedClient';
import type { SystemInfoResponse, AppConfigResponse } from '../types/api';

export interface BackupJob {
  id: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  triggeredByAdminId?: number;
  triggeredByAdminEmail?: string;
  triggerType: 'AUTO_CRON' | 'MANUAL';
  startedAt?: string;
  finishedAt?: string;
  filePath?: string;
  fileSizeBytes?: number;
  errorMessage?: string;
  createdAt: string;
}

export type SystemInfo = SystemInfoResponse;
export type AppConfig = AppConfigResponse;

export interface MatchingConfig {
  enabled: boolean;
  weightDistance: number;
  weightInterest: number;
  weightHobby: number;
  weightTemperament: number;
  boostRankBonus: number;
}

export interface ExperimentConfig {
  enabled: boolean;
  splitPct: number;
  saltVersion: number;
  hasSalt: boolean;
}

export interface AdminUser {
  id: number;
  email: string;
  name: string;
  role: string;
  isActive: boolean;
  mustChangePassword: boolean;
  lastLogin: string | null;
}

export interface AdminCreateRequest {
  email: string;
  name: string;
  role: string;
  temporaryPassword?: string;
}

export interface AdminCreateResponse {
  id: number;
  email: string;
  name: string;
  role: string;
  isActive: boolean;
  mustChangePassword: boolean;
  temporaryPassword?: string;
}

export interface AdminUpdateRequest {
  name?: string;
  role?: string;
  isActive?: boolean;
}

export const systemService = {
  async getSystemInfo(): Promise<SystemInfo> {
    const response = await typedClient.get('/api/v1/admin/system/info');
    return response.data as SystemInfo;
  },

  async getAdmins(): Promise<AdminUser[]> {
    const response = await typedClient.get('/api/v1/admin/system/admins');
    return response.data as unknown as AdminUser[];
  },

  async getMaintenanceStatus(): Promise<{ enabled: boolean }> {
    const response = await typedClient.get('/api/v1/admin/system/maintenance');
    return response.data as unknown as { enabled: boolean };
  },

  async setMaintenanceMode(enabled: boolean): Promise<void> {
    await typedClient.post('/api/v1/admin/system/maintenance', { enabled });
  },

  async getConfigs(): Promise<AppConfig[]> {
    const response = await typedClient.get('/api/v1/admin/system/configs');
    return response.data as AppConfig[];
  },

  async updateConfig(key: string, value: string): Promise<void> {
    await typedClient.post('/api/v1/admin/system/configs', { key, value });
  },

  async getMatchingConfig(): Promise<MatchingConfig> {
    const response = await typedClient.get('/api/v1/admin/system/matching-config');
    return response.data as unknown as MatchingConfig;
  },

  async updateMatchingConfig(cfg: MatchingConfig): Promise<void> {
    await typedClient.put('/api/v1/admin/system/matching-config', cfg);
  },

  async getExperimentConfig(): Promise<ExperimentConfig> {
    const response = await typedClient.get('/api/v1/admin/system/experiment-config');
    return response.data as unknown as ExperimentConfig;
  },

  async updateExperimentConfig(cfg: { enabled: boolean; splitPct: number }): Promise<void> {
    await typedClient.put('/api/v1/admin/system/experiment-config', cfg);
  },

  async createAdmin(data: AdminCreateRequest): Promise<AdminCreateResponse> {
    const body = data as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/system/admins'>>[1];
    const response = await typedClient.post('/api/v1/admin/system/admins', body);
    return response.data as unknown as AdminCreateResponse;
  },

  async updateAdmin(id: number, data: AdminUpdateRequest): Promise<AdminUser> {
    const body = data as unknown as Parameters<typeof typedClient.putPath<'/api/v1/admin/system/admins/{id}'>>[2];
    const response = await typedClient.putPath('/api/v1/admin/system/admins/{id}', { id }, body);
    return response.data as unknown as AdminUser;
  },

  async deleteAdmin(id: number): Promise<void> {
    await typedClient.deletePath('/api/v1/admin/system/admins/{id}', { id });
  },

  async resetAdminPassword(id: number): Promise<{ temporaryPassword: string }> {
    const body = {} as unknown as Parameters<typeof typedClient.postPath<'/api/v1/admin/system/admins/{id}/reset-password'>>[2];
    const response = await typedClient.postPath('/api/v1/admin/system/admins/{id}/reset-password', { id }, body);
    return response.data as unknown as { temporaryPassword: string };
  },

  async triggerBackup(): Promise<BackupJob> {
    const body = undefined as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/system/backup'>>[1];
    const response = await typedClient.post('/api/v1/admin/system/backup', body);
    return response.data as unknown as BackupJob;
  },

  async getBackupJobs(limit = 20): Promise<BackupJob[]> {
    const response = await typedClient.get('/api/v1/admin/system/backup/jobs', { params: { limit } });
    return response.data as unknown as BackupJob[];
  },
};
