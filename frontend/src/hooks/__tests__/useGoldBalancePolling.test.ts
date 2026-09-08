/**
 * useGoldBalancePolling 단위 테스트
 *
 * Acceptance criteria:
 * (a) 매 poll에 직전 ETag로 If-None-Match present
 * (b) HTTP 304 → queryClient.invalidateQueries 미호출
 * (c) HTTP 200 → queryClient.invalidateQueries 호출
 * (d) polling 간격 = 명시값(GOLD_BALANCE_POLL_INTERVAL_MS = 10 s)
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';

// ── hoisted mocks ────────────────────────────────────────────────────────────
const { mockGetBalanceWithETag, mockInvalidateQueries } = vi.hoisted(() => ({
  mockGetBalanceWithETag: vi.fn(),
  mockInvalidateQueries: vi.fn(),
}));

vi.mock('../../services/goldService', () => ({
  goldService: { getBalanceWithETag: mockGetBalanceWithETag },
}));

vi.mock('../../lib/queryClient', () => ({
  queryClient: { invalidateQueries: mockInvalidateQueries },
}));

import { useGoldBalancePolling, GOLD_BALANCE_POLL_INTERVAL_MS } from '../useGoldBalancePolling';

describe('useGoldBalancePolling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('(d) polling 간격이 GOLD_BALANCE_POLL_INTERVAL_MS(10 s)이다', async () => {
    mockGetBalanceWithETag.mockResolvedValue({ status: 304, data: null, etag: null });

    renderHook(() => useGoldBalancePolling());

    // 10 s 경과 전 → 미호출
    await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS - 1);
    expect(mockGetBalanceWithETag).not.toHaveBeenCalled();

    // 10 s 경과 → 1회 호출
    await vi.advanceTimersByTimeAsync(1);
    expect(mockGetBalanceWithETag).toHaveBeenCalledTimes(1);

    // 20 s 경과 → 2회 호출
    await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS);
    expect(mockGetBalanceWithETag).toHaveBeenCalledTimes(2);
  });

  it('(a) 첫 poll은 null로 시작하고, 200 응답 후 두 번째 poll은 ETag를 전달한다', async () => {
    mockGetBalanceWithETag
      .mockResolvedValueOnce({ status: 200, data: { balance: 1000 }, etag: '"etag-v1"' })
      .mockResolvedValue({ status: 304, data: null, etag: null });

    renderHook(() => useGoldBalancePolling());

    // 첫 poll — null 전달
    await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS);
    expect(mockGetBalanceWithETag).toHaveBeenNthCalledWith(1, null);

    // 두 번째 poll — 직전 ETag 전달
    await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS);
    expect(mockGetBalanceWithETag).toHaveBeenNthCalledWith(2, '"etag-v1"');
  });

  it('(b) 304 응답 시 invalidateQueries를 호출하지 않는다', async () => {
    mockGetBalanceWithETag.mockResolvedValue({ status: 304, data: null, etag: null });

    renderHook(() => useGoldBalancePolling());

    await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS);

    expect(mockInvalidateQueries).not.toHaveBeenCalled();
  });

  it('(c) 200 응답 시 invalidateQueries를 gold balance 쿼리키로 호출한다', async () => {
    mockGetBalanceWithETag.mockResolvedValue({
      status: 200,
      data: { balance: 500 },
      etag: '"etag-abc"',
    });

    renderHook(() => useGoldBalancePolling());

    await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS);

    expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['gold', 'balance'] });
  });

  it('unmount 시 폴링이 중단된다', async () => {
    mockGetBalanceWithETag.mockResolvedValue({ status: 304, data: null, etag: null });

    const { unmount } = renderHook(() => useGoldBalancePolling());
    unmount();

    await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS * 3);
    expect(mockGetBalanceWithETag).not.toHaveBeenCalled();
  });

  it('커스텀 intervalMs를 사용한다', async () => {
    mockGetBalanceWithETag.mockResolvedValue({ status: 304, data: null, etag: null });

    renderHook(() => useGoldBalancePolling(5_000));

    await vi.advanceTimersByTimeAsync(5_000);
    expect(mockGetBalanceWithETag).toHaveBeenCalledTimes(1);
  });

  // PERF-022: 화면이 안 보이는 동안(탭 전환·앱 최소화)에도 10초마다 계속 요청하고 있었다.
  describe('(e) document.hidden 가드', () => {
    afterEach(() => {
      vi.spyOn(document, 'hidden', 'get').mockRestore();
    });

    it('백그라운드에서는 폴링 요청을 보내지 않는다', async () => {
      vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
      mockGetBalanceWithETag.mockResolvedValue({ status: 304, data: null, etag: null });

      renderHook(() => useGoldBalancePolling());

      await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS * 3);
      expect(mockGetBalanceWithETag).not.toHaveBeenCalled();
    });

    it('포그라운드로 돌아오면 다음 tick 에서 폴링을 재개한다', async () => {
      const hiddenSpy = vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
      mockGetBalanceWithETag.mockResolvedValue({ status: 304, data: null, etag: null });

      renderHook(() => useGoldBalancePolling());

      await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS);
      expect(mockGetBalanceWithETag).not.toHaveBeenCalled();

      hiddenSpy.mockReturnValue(false);
      await vi.advanceTimersByTimeAsync(GOLD_BALANCE_POLL_INTERVAL_MS);
      expect(mockGetBalanceWithETag).toHaveBeenCalledTimes(1);
    });
  });
});
