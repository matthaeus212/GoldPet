import { typedClient } from './typedClient';
import type {
  PageResponse,
  UserStatus,
  UserListItemResponse,
  UserDetailResponse,
  TransactionAdminResponse,
} from '../types/api';

// Generated aliases (Phase 3.11.2).
export type UserListItem = UserListItemResponse;
export type UserDetail = UserDetailResponse;
export type UserGoldTransaction = TransactionAdminResponse;

export const userManagementService = {
  async getUsers(params: {
    page?: number;
    size?: number;
    search?: string;
    status?: string;
    sortBy?: string;
    sortDir?: string;
  }): Promise<PageResponse<UserListItem>> {
    const response = await typedClient.get('/api/v1/admin/users', {
      params: params as Record<string, string | number | undefined>,
    });
    return response.data as unknown as PageResponse<UserListItem>;
  },

  async getUserDetail(userId: number): Promise<UserDetail> {
    const response = await typedClient.getPath(
      '/api/v1/admin/users/{userId}',
      { userId },
    );
    return response.data as UserDetail;
  },

  async updateUserStatus(userId: number, status: UserStatus, reason?: string): Promise<void> {
    await typedClient.patchPath(
      '/api/v1/admin/users/{userId}/status',
      { userId },
      { status, reason } as unknown as Parameters<typeof typedClient.patchPath<'/api/v1/admin/users/{userId}/status'>>[2],
    );
  },

  async deleteUser(userId: number): Promise<void> {
    await typedClient.deletePath('/api/v1/admin/users/{userId}', { userId });
  },

  async unlockUserProfile(userId: number): Promise<void> {
    await typedClient.putPath('/api/v1/admin/users/{userId}/unlock-profile', { userId }, undefined);
  },

  async searchUsers(query: string, limit = 10): Promise<UserListItem[]> {
    const response = await typedClient.get('/api/v1/admin/users/search', {
      params: { query, limit },
    });
    return response.data as UserListItem[];
  },

  async getGoldTransactions(
    userId: number,
    params: { page?: number; size?: number } = {},
  ): Promise<PageResponse<UserGoldTransaction>> {
    const response = await typedClient.getPath(
      '/api/v1/admin/users/{userId}/gold-transactions',
      { userId },
      { params },
    );
    return response.data as unknown as PageResponse<UserGoldTransaction>;
  },
};
