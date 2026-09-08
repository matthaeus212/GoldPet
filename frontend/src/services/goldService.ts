import { typedClient } from './api/typedClient';
import type {
  GoldBalanceResponse,
  GoldProductResponse,
  TransactionResponse,
  ChargeRequest,
  SpendRequest,
} from '../types/api';

// Generated aliases (Phase 3.10). Backend names are `ChargeRequest`/`SpendRequest`
// (no "Gold" prefix); legacy frontend names kept for caller-side compatibility.
export type GoldBalance = GoldBalanceResponse;
export type GoldProduct = GoldProductResponse;
export type GoldTransaction = TransactionResponse;
export type ChargeGoldRequest = ChargeRequest;
export type SpendGoldRequest = SpendRequest;

export interface GoldBalanceETagResult {
  status: 200 | 304;
  data: GoldBalance | null;
  etag: string | null;
}

// Service - real API calls only, no mock fallbacks
export const goldService = {
  getBalance: async (): Promise<GoldBalance> => {
    const response = await typedClient.get('/api/v1/gold/balance');
    return response.data;
  },

  /**
   * ETag 기반 잔액 조회.
   * - ifNoneMatch: 직전 ETag 값 (첫 호출 시 null)
   * - 200: 새 잔액 + ETag 반환
   * - 304: data=null, etag=null (변경 없음 — invalidateQueries 금지)
   */
  getBalanceWithETag: async (ifNoneMatch: string | null): Promise<GoldBalanceETagResult> => {
    const headers: Record<string, string> = {};
    if (ifNoneMatch) {
      headers['If-None-Match'] = ifNoneMatch;
    }
    const response = await typedClient.get('/api/v1/gold/balance', {
      headers,
      validateStatus: (s) => s === 200 || s === 304,
    });
    const etag = (response.headers as Record<string, string>)['etag'] ?? null;
    if (response.status === 304) {
      return { status: 304, data: null, etag: null };
    }
    return { status: 200, data: response.data, etag };
  },

  getProducts: async (): Promise<GoldProduct[]> => {
    const response = await typedClient.get('/api/v1/gold/products');
    return response.data;
  },

  getTransactions: async (
    page = 0,
    size = 20,
    type?: string
  ): Promise<{ content: GoldTransaction[]; totalPages: number; totalElements: number }> => {
    const params: Record<string, string | number> = { page, size };
    if (type && type !== 'ALL') params.type = type;
    const response = await typedClient.get('/api/v1/gold/transactions', { params });
    return response.data as { content: GoldTransaction[]; totalPages: number; totalElements: number };
  },

  chargeGold: async (
    request: ChargeGoldRequest
  ): Promise<GoldTransaction> => {
    const response = await typedClient.post('/api/v1/gold/charge', request);
    return response.data;
  },

  spendGold: async (
    request: SpendGoldRequest
  ): Promise<GoldTransaction> => {
    const response = await typedClient.post('/api/v1/gold/spend', request);
    return response.data;
  },
};
