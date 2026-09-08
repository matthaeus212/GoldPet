// 네이티브 브리지 런타임(요청/응답, 타임아웃, origin 검증, 이벤트 버퍼) 테스트 (ARCH-006)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * ARCH-006: native_bridge.dart(467행)와 nativeBridge.ts 양쪽에 유닛 테스트가 하나도 없었다.
 * 브리지는 인증 토큰 동기화·산책 시작 같은 핵심 경로를 지나가므로, 조용한 회귀가 가장 비싼 곳이다.
 *
 * Flutter 쪽 라우팅 계약은 bridge_contract_test.dart 가, URL/파일명 가드는
 * bridge_url_guard_test.dart(SEC-007)가 맡는다. 여기서는 React 런타임을 검증한다.
 */

type PostedMessage = { id: string; method: string; params?: Record<string, unknown> };

/** window.NativeBridge(Flutter JS 채널)를 흉내내고 보낸 메시지를 모은다. */
function installChannel(): PostedMessage[] {
  const posted: PostedMessage[] = [];
  (window as unknown as Record<string, unknown>).NativeBridge = {
    postMessage: (raw: string) => posted.push(JSON.parse(raw)),
  };
  return posted;
}

function respond(response: { id: string; success: boolean; data?: unknown; error?: { code: string; message: string } }) {
  const bridge = (window as unknown as Record<string, unknown>).nativeBridge as {
    handleResponse: (r: unknown) => void;
  };
  bridge.handleResponse({ ...response, timestamp: Date.now() });
}

/** 모듈 싱글턴이라 테스트마다 새로 import 해 상태를 격리한다. */
async function freshBridge() {
  vi.resetModules();
  const mod = await import('../nativeBridge');
  return mod.nativeBridge;
}

describe('nativeBridge — 요청/응답', () => {
  beforeEach(() => {
    delete (window as unknown as Record<string, unknown>).NativeBridge;
    delete (window as unknown as Record<string, unknown>).nativeBridge;
  });

  it('callMethod 가 Flutter 채널로 method/params 를 실어 보낸다', async () => {
    const posted = installChannel();
    const bridge = await freshBridge();

    const call = bridge.callMethod('syncAuthToken', { accessToken: 'a', refreshToken: 'r' });

    expect(posted).toHaveLength(1);
    expect(posted[0].method).toBe('syncAuthToken');
    expect(posted[0].params).toEqual({ accessToken: 'a', refreshToken: 'r' });

    respond({ id: posted[0].id, success: true, data: { success: true } });
    await expect(call).resolves.toEqual({ success: true });
  });

  it('실패 응답은 error.message 로 reject 된다', async () => {
    const posted = installChannel();
    const bridge = await freshBridge();

    const call = bridge.callMethod('getCurrentLocation');
    respond({
      id: posted[0].id,
      success: false,
      error: { code: 'PERMISSION_DENIED', message: '위치 권한이 없습니다' },
    });

    await expect(call).rejects.toThrow('위치 권한이 없습니다');
  });

  // 채널이 없는데(웹 브라우저) 호출하면 매달린 Promise 로 남지 않고 즉시 실패해야 한다.
  it('Flutter 채널이 없으면 즉시 reject 한다', async () => {
    const bridge = await freshBridge();
    await expect(bridge.callMethod('getCurrentLocation')).rejects.toThrow(
      'NativeBridge channel not available',
    );
  });

  it('응답이 없으면 30초 뒤 타임아웃으로 reject 한다', async () => {
    vi.useFakeTimers();
    try {
      installChannel();
      const bridge = await freshBridge();

      const call = bridge.callMethod('openCamera');
      const assertion = expect(call).rejects.toThrow('Bridge call timeout: openCamera');

      await vi.advanceTimersByTimeAsync(30_000);
      await assertion;
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('nativeBridge — 이벤트', () => {
  beforeEach(() => {
    delete (window as unknown as Record<string, unknown>).NativeBridge;
    delete (window as unknown as Record<string, unknown>).nativeBridge;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('postMessage 로 온 이벤트를 리스너에 전달한다', async () => {
    const bridge = await freshBridge();
    const seen: unknown[] = [];
    bridge.onEvent('walkCompleted', (d) => seen.push(d));

    window.dispatchEvent(
      new MessageEvent('message', {
        data: { type: 'walkCompleted', data: { walkId: 7 } },
        origin: window.location.origin,
      }),
    );

    expect(seen).toEqual([{ walkId: 7 }]);
  });

  // 외부 origin 이 브리지 이벤트를 위조하면 인증 만료·토큰 동기화를 임의로 트리거할 수 있다.
  it('알 수 없는 origin 의 메시지는 무시한다', async () => {
    const bridge = await freshBridge();
    const seen: unknown[] = [];
    bridge.onEvent('authExpired', (d) => seen.push(d));

    window.dispatchEvent(
      new MessageEvent('message', {
        data: { type: 'authExpired', data: {} },
        origin: 'https://evil.example.com',
      }),
    );

    expect(seen).toEqual([]);
  });

  // 콜드 부팅: 알림 클릭 이벤트가 React 마운트보다 먼저 도착해도 유실되면 안 된다.
  it('리스너 등록 전에 도착한 버퍼 대상 이벤트를 나중에 재생한다', async () => {
    const bridge = await freshBridge();

    window.dispatchEvent(
      new MessageEvent('message', {
        data: { type: 'pushNotificationClicked', data: { link: '/chat/1' } },
        origin: window.location.origin,
      }),
    );

    const seen: unknown[] = [];
    bridge.onEvent('pushNotificationClicked', (d) => seen.push(d));

    // 재생은 microtask 로 예약된다.
    await Promise.resolve();
    await Promise.resolve();
    expect(seen).toEqual([{ link: '/chat/1' }]);
  });

  it('버퍼 대상이 아닌 이벤트는 재생하지 않는다', async () => {
    const bridge = await freshBridge();

    window.dispatchEvent(
      new MessageEvent('message', {
        data: { type: 'locationUpdated', data: { lat: 1 } },
        origin: window.location.origin,
      }),
    );

    const seen: unknown[] = [];
    bridge.onEvent('locationUpdated', (d) => seen.push(d));
    await Promise.resolve();
    expect(seen).toEqual([]);
  });

  it('구독 해제 후에는 이벤트를 받지 않는다', async () => {
    const bridge = await freshBridge();
    const seen: unknown[] = [];
    const off = bridge.onEvent('walkCompleted', (d) => seen.push(d));
    off();

    window.dispatchEvent(
      new MessageEvent('message', {
        data: { type: 'walkCompleted', data: { walkId: 1 } },
        origin: window.location.origin,
      }),
    );

    expect(seen).toEqual([]);
  });
});
