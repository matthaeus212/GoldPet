import apiClient from './api/client';

export interface ProfileBoostDetail {
  id: number;
  startedAt: string;        // ISO timestamp
  expiresAt: string;        // ISO timestamp
  goldCost: number;
  active: boolean;
  remainingSeconds: number;
}

export interface ActiveBoostResponse {
  active: boolean;
  boost: ProfileBoostDetail | null;
}

export const profileBoostService = {
  // Uses apiClient — endpoints are not yet in the generated OpenAPI schema.
  getActiveBoost: async (): Promise<ActiveBoostResponse> => {
    const response = await apiClient.get<ActiveBoostResponse>('/profile-boost/active');
    return response.data;
  },

  // Requires a fresh Idempotency-Key UUID per purchase attempt to prevent double-spend.
  // 400 → insufficient gold; 409 → key reuse (retry with new key).
  purchaseBoost: async (idempotencyKey: string): Promise<ProfileBoostDetail> => {
    const response = await apiClient.post<ProfileBoostDetail>(
      '/profile-boost/purchase',
      null,
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
    return response.data;
  },
};
