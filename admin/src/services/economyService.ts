import { typedClient } from './typedClient';
import type {
  PageResponse,
  EconomyStatsResponse,
  TransactionAdminResponse,
  GoldProductAdminResponse,
  GoldProductCreateRequest,
  GoldProductUpdateRequest,
} from '../types/api';

// Generated aliases (Phase 3.11.4). TransactionItem (admin tx list rows) maps to
// TransactionAdminResponse — the admin response carries userNickname while the
// user-facing TransactionResponse does not.
export type EconomyStats = EconomyStatsResponse;
export type TransactionItem = TransactionAdminResponse;
export type GoldProductAdmin = GoldProductAdminResponse;
export type { GoldProductCreateRequest, GoldProductUpdateRequest };

export const economyService = {
  async getStats(): Promise<EconomyStats> {
    const response = await typedClient.get('/api/v1/admin/economy/stats');
    return response.data as EconomyStats;
  },

  async getTransactions(params: { page?: number; type?: string }): Promise<PageResponse<TransactionItem>> {
    const response = await typedClient.get('/api/v1/admin/economy/transactions', { params });
    return response.data as unknown as PageResponse<TransactionItem>;
  },

  async adjustGold(userId: number, amount: number, reason: string): Promise<void> {
    await typedClient.post('/api/v1/admin/economy/adjust', { userId, amount, reason });
  },

  async getProducts(): Promise<GoldProductAdmin[]> {
    const response = await typedClient.get('/api/v1/admin/economy/products');
    return response.data as GoldProductAdmin[];
  },

  async createProduct(request: GoldProductCreateRequest): Promise<GoldProductAdmin> {
    const response = await typedClient.post('/api/v1/admin/economy/products', request);
    return response.data as GoldProductAdmin;
  },

  async updateProduct(id: number, request: GoldProductUpdateRequest): Promise<GoldProductAdmin> {
    const response = await typedClient.putPath(
      '/api/v1/admin/economy/products/{id}',
      { id },
      request,
    );
    return response.data as GoldProductAdmin;
  },

  async deleteProduct(id: number): Promise<void> {
    await typedClient.deletePath('/api/v1/admin/economy/products/{id}', { id });
  },

  async refundTransaction(transactionId: number, reason: string): Promise<void> {
    await typedClient.post('/api/v1/admin/economy/refund', { transactionId, reason });
  },
};
