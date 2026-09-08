/**
 * client.ts 인터셉터 단위 테스트
 *
 * 검증:
 * 1. 요청 인터셉터: deviceId 보유 시 X-Device-Id 헤더 첨부
 * 2. 요청 인터셉터: deviceId 없을 때 X-Device-Id 헤더 미첨부
 * 3. refresh POST (401 인터셉터): X-Device-Id 헤더 포함
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { AxiosRequestConfig } from 'axios';

// ── hoisted mocks (vi.mock factory는 상단으로 hoisting되므로 변수를 먼저 선언) ──
const { mockGetState, mockAxiosPost } = vi.hoisted(() => ({
  mockGetState: vi.fn(),
  mockAxiosPost: vi.fn(),
}));

vi.mock('../../../stores/authStore', () => ({
  useAuthStore: { getState: mockGetState },
}));

vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>();
  return {
    ...actual,
    default: {
      ...actual.default,
      create: actual.default.create,
      post: mockAxiosPost,
    },
  };
});

// window.location.href 조작 허용
Object.defineProperty(window, 'location', {
  value: { href: '' },
  writable: true,
});

import { apiClient } from '../client';

// axios 내부 interceptors.handlers 접근 헬퍼
const getRequestInterceptorFn = () => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handlers = (apiClient.interceptors.request as any).handlers as Array<{
    fulfilled: (config: AxiosRequestConfig) => AxiosRequestConfig;
  }>;
  return handlers[0].fulfilled;
};

describe('client.ts — 요청 인터셉터', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('deviceId가 있으면 X-Device-Id 헤더를 추가한다', () => {
    mockGetState.mockReturnValue({ token: 'tok', deviceId: 'test-device-uuid' });

    const interceptor = getRequestInterceptorFn();
    const config = { headers: {} as Record<string, string> };
    const result = interceptor(config as AxiosRequestConfig);

    expect((result.headers as Record<string, string>)['X-Device-Id']).toBe('test-device-uuid');
  });

  it('deviceId가 없으면 X-Device-Id 헤더를 추가하지 않는다', () => {
    mockGetState.mockReturnValue({ token: 'tok', deviceId: null });

    const interceptor = getRequestInterceptorFn();
    const config = { headers: {} as Record<string, string> };
    const result = interceptor(config as AxiosRequestConfig);

    expect((result.headers as Record<string, string>)['X-Device-Id']).toBeUndefined();
  });

  it('token이 있으면 Authorization 헤더를 추가한다', () => {
    mockGetState.mockReturnValue({ token: 'bearer-token', deviceId: null });

    const interceptor = getRequestInterceptorFn();
    const config = { headers: {} as Record<string, string> };
    const result = interceptor(config as AxiosRequestConfig);

    expect((result.headers as Record<string, string>)['Authorization']).toBe('Bearer bearer-token');
  });
});

describe('client.ts — refresh POST X-Device-Id', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAxiosPost.mockResolvedValue({
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    });
  });

  it('401 refresh 요청에 X-Device-Id 헤더를 포함한다', async () => {
    const mockSetTokens = vi.fn();
    const mockLogout = vi.fn();
    mockGetState.mockReturnValue({
      token: 'old-token',
      refreshToken: 'old-refresh',
      deviceId: 'device-abc',
      setTokens: mockSetTokens,
      logout: mockLogout,
    });

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const responseInterceptorError = (apiClient.interceptors.response as any).handlers[0].rejected;

    const mockError = {
      response: { status: 401 },
      config: { _retry: false, headers: {} },
    };

    await responseInterceptorError(mockError).catch(() => {});

    expect(mockAxiosPost).toHaveBeenCalledWith(
      expect.stringContaining('/auth/refresh'),
      { refreshToken: 'old-refresh' },
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Device-Id': 'device-abc' }),
      }),
    );
  });
});
