/**
 * useChatWebSocket — self-echo (clientMsgId) 및 중복 제거 로직 단위 테스트.
 *
 * 전략:
 * - chatWebSocket 싱글톤을 vi.mock 으로 교체, subscribeToRoom 콜백을 직접 캡처.
 * - QueryClient 캐시를 미리 세팅하거나 캡처된 핸들러를 호출해 상태 변화 검증.
 * - JSX 없이 createElement 로 wrapper 구성 → .ts 파일 유지.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createElement } from 'react';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// ── 모킹: chatWebSocket 싱글톤 (vi.hoisted 로 factory 호이스팅 문제 해소) ──

const { mockWS } = vi.hoisted(() => ({
  mockWS: {
    connect: vi.fn().mockResolvedValue(undefined),
    subscribeToRoom: vi.fn(),
    subscribeToTyping: vi.fn(),
    subscribeToTopic: vi.fn(),
    unsubscribeFromRoom: vi.fn(),
    unsubscribeFromTyping: vi.fn(),
    unsubscribeFromTopic: vi.fn(),
    sendMessage: vi.fn(),
    sendTypingEvent: vi.fn(),
    connected: true,
  },
}));

vi.mock('../../services/websocket/chatWebSocket', () => ({
  chatWebSocket: mockWS,
}));

vi.mock('../../services/chatService', () => ({
  chatService: {
    markAsRead: vi.fn().mockResolvedValue(undefined),
    // T-chat-latency-v2 Step 4 — debounced variant invoked on each WS message burst.
    markAsReadDebounced: vi.fn(),
    cancelMarkAsReadDebounced: vi.fn(),
  },
}));

vi.mock('../../utils/chatMetrics', () => ({
  chatMetrics: {
    recordRtt: vi.fn(),
    recordImageUpload: vi.fn(),
  },
}));

import { useChatWebSocket } from '../useChatWebSocket';
import type { ChatMessage } from '../../services/chatService';

// ── 헬퍼 ──────────────────────────────────────────────────────────────────

function makeMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: 1,
    roomId: 1,
    senderId: 200,
    senderNickname: '상대방',
    messageType: 'TEXT',
    textContent: '테스트 메시지',
    timestamp: new Date().toISOString(),
    type: 'text',
    ...overrides,
  } as ChatMessage;
}

// ── describe ──────────────────────────────────────────────────────────────

describe('useChatWebSocket — handleMessage', () => {
  const ROOM_ID = '42';
  let queryClient: QueryClient;
  let capturedHandler: ((msg: unknown) => void) | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    // subscribeToRoom 호출 시 핸들러 캡처
    mockWS.subscribeToRoom.mockImplementation((_roomId: string, handler: (m: unknown) => void) => {
      capturedHandler = handler;
    });
  });

  async function renderWSHook() {
    const result = renderHook(
      () => useChatWebSocket({ roomId: ROOM_ID, currentUserId: 100, enabled: true }),
      {
        wrapper: ({ children }) =>
          createElement(QueryClientProvider, { client: queryClient }, children),
      },
    );
    // connect() 는 async → subscribeToRoom 이 microtask 큐 후 실행됨. flush 필요.
    await act(async () => {});
    return result;
  }

  // ── self-echo ──────────────────────────────────────────────────────────

  it('clientMsgId 가 일치하는 낙관적 row 를 서버 메시지로 교체 (중복 추가 없음)', async () => {
    // Given: 캐시에 낙관적 메시지 미리 적재
    const optimistic = makeMessage({
      id: 'temp-abc' as unknown as number,
      clientMsgId: 'uuid-abc',
      _optimistic: true,
      _sentAt: Date.now() - 100,
      type: 'text',
    });
    queryClient.setQueryData(['chatMessages', ROOM_ID], [optimistic]);

    await renderWSHook();

    // When: 서버 echo — 같은 clientMsgId, 진짜 id
    const serverMsg = makeMessage({ id: 99, clientMsgId: 'uuid-abc', _optimistic: false });

    await act(async () => {
      capturedHandler?.(serverMsg);
    });

    // Then: 캐시는 여전히 1건, 낙관적 row 가 서버 row 로 교체
    const messages = queryClient.getQueryData<ChatMessage[]>(['chatMessages', ROOM_ID])!;
    expect(messages).toHaveLength(1);
    expect(messages[0].id).toBe(99);
    expect(messages[0]._optimistic).toBeFalsy();
  });

  it('clientMsgId 없는 서버 메시지는 캐시 끝에 추가', async () => {
    // Given: 기존 메시지 1건
    const existing = makeMessage({ id: 1 });
    queryClient.setQueryData(['chatMessages', ROOM_ID], [existing]);

    await renderWSHook();

    // When: clientMsgId 없는 새 메시지
    const newMsg = makeMessage({ id: 2, textContent: '새 메시지' });

    await act(async () => {
      capturedHandler?.(newMsg);
    });

    // Then: 기존 + 신규 = 2건
    const messages = queryClient.getQueryData<ChatMessage[]>(['chatMessages', ROOM_ID])!;
    expect(messages).toHaveLength(2);
    expect(messages[1].id).toBe(2);
  });

  it('이미 캐시에 있는 id 의 서버 메시지는 중복 추가 안 함', async () => {
    // Given: id=5 가 이미 캐시에 있음
    const existing = makeMessage({ id: 5 });
    queryClient.setQueryData(['chatMessages', ROOM_ID], [existing]);

    await renderWSHook();

    // When: 같은 id=5 로 다시 수신
    const duplicate = makeMessage({ id: 5, textContent: '중복 메시지' });

    await act(async () => {
      capturedHandler?.(duplicate);
    });

    // Then: 여전히 1건 (중복 차단)
    const messages = queryClient.getQueryData<ChatMessage[]>(['chatMessages', ROOM_ID])!;
    expect(messages).toHaveLength(1);
    expect(messages[0].textContent).toBe('테스트 메시지'); // 원본 유지
  });

  it('캐시가 비어있을 때 수신된 메시지를 첫 원소로 설정', async () => {
    await renderWSHook();

    // When: 캐시 비어있는 상태에서 첫 메시지 수신
    const firstMsg = makeMessage({ id: 10 });

    await act(async () => {
      capturedHandler?.(firstMsg);
    });

    // Then: [firstMsg]
    const messages = queryClient.getQueryData<ChatMessage[]>(['chatMessages', ROOM_ID])!;
    expect(messages).toHaveLength(1);
    expect(messages[0].id).toBe(10);
  });
});
