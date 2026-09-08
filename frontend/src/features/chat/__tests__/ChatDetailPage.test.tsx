/**
 * ChatDetailPage — 낙관적 UI 렌더링 단위 테스트.
 *
 * 전략: QueryClient 에 메시지를 미리 세팅 → 렌더링 결과의 CSS 클래스로 검증.
 * 실제 API/WebSocket 호출은 모두 vi.mock 으로 차단.
 *
 * 검증 대상:
 *  1. _optimistic=true + _rejectedReason 없음  → `.chat_detail_message_optimistic` 클래스
 *  2. _rejectedReason 5종                       → `.chat_detail_message_rejected_<type>` 클래스
 *  3. 일반 메시지(_optimistic 없음)              → 위 클래스 없음
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// ── CSS 모킹 ──────────────────────────────────────────────────────────────
vi.mock('../../../styles/chat-detail.css', () => ({}));
vi.mock('../../../assets/css/common.css', () => ({}));

// ── 내부 훅 / 서비스 모킹 ────────────────────────────────────────────────
vi.mock('../../../hooks/useOverlayColor', () => ({ useOverlayColor: () => [] }));
vi.mock('../../../hooks/useKeyboardDismiss', () => ({ useKeyboardDismiss: () => {} }));

vi.mock('../../../hooks/useChatWebSocket', () => ({
  useChatWebSocket: () => ({
    sendMessage: vi.fn(),
    sendTypingEvent: vi.fn(),
    typingUsers: new Set(),
    isConnected: false,
  }),
}));

vi.mock('../../../services/chatService', () => ({
  chatService: {
    getMessages: vi.fn().mockResolvedValue([]),
    sendMessage: vi.fn().mockResolvedValue({ id: 999 }),
    getChatRooms: vi.fn().mockResolvedValue([]),
    markAsRead: vi.fn().mockResolvedValue(undefined),
    leaveRoom: vi.fn().mockResolvedValue(undefined),
    getParticipants: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock('../../../stores/authStore', () => ({
  useAuthStore: (selector?: (s: unknown) => unknown) => {
    const state = { user: { id: 100, nickname: 'TestUser', profileImageUrl: null } };
    return selector ? selector(state) : state;
  },
}));

vi.mock('../../../bridge/nativeBridge', () => ({
  nativeBridge: { callMethod: vi.fn(), openCamera: vi.fn(), registerFcmToken: vi.fn() },
}));

vi.mock('../../../services/blockService', () => ({
  blockService: { blockUser: vi.fn(), unblockUser: vi.fn() },
}));

vi.mock('../../../services/reportService', () => ({
  reportService: { createReport: vi.fn() },
}));

vi.mock('../../../contexts/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

vi.mock('../../../contexts/AlertContext', () => ({
  useAlert: () => ({ showAlert: vi.fn() }),
}));

vi.mock('../EmoticonPicker', () => ({ default: () => null }));
vi.mock('../InviteGroupChatModal', () => ({ default: () => null }));

vi.mock('../../../components/common/GpImage', () => ({
  GpImage: ({ alt }: { alt?: string }) => React.createElement('img', { alt, 'data-testid': 'gp-image' }),
}));
vi.mock('../../../components/common/Loading', () => ({
  Loading: () => React.createElement('div', { 'data-testid': 'loading' }),
}));
vi.mock('../../../components/common/BackButton', () => ({
  BackButton: () => React.createElement('button', {}, '뒤로'),
}));
vi.mock('../../../components/common/ReportModal', () => ({
  ReportModal: () => null,
}));
vi.mock('../../../config/queryConfig', () => ({
  CACHE_TIME: {
    STATIC: { staleTime: Infinity, gcTime: 86400000 },
    SEMI_STATIC: { staleTime: 3600000, gcTime: 86400000 },
    DYNAMIC: { staleTime: 60000, gcTime: 300000 },
    REAL_TIME: { staleTime: 10000, gcTime: 120000 },
    FRESH: { staleTime: 0, gcTime: 300000 },
  },
}));

import ChatDetailPage from '../ChatDetailPage';

// ── 타입 ──────────────────────────────────────────────────────────────────
import type { ChatMessage } from '../../../services/chatService';

// ── 헬퍼 ──────────────────────────────────────────────────────────────────

const CHAT_ID = '1';

function makeChatMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: 1,
    roomId: 1,
    senderId: 100,
    senderNickname: 'TestUser',
    messageType: 'TEXT',
    textContent: '메시지 내용',
    timestamp: new Date().toISOString(),
    type: 'text',
    ...overrides,
  } as ChatMessage;
}

function renderPage(messages: ChatMessage[]) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  // 메시지 캐시 미리 세팅 (실제 fetch 없이 렌더링)
  qc.setQueryData(['chatMessages', CHAT_ID], messages);

  return render(
    React.createElement(
      QueryClientProvider,
      { client: qc },
      React.createElement(
        MemoryRouter,
        { initialEntries: [`/chat/${CHAT_ID}`] },
        React.createElement(
          Routes,
          null,
          React.createElement(Route, {
            path: '/chat/:chatId',
            element: React.createElement(ChatDetailPage),
          }),
        ),
      ),
    ),
  );
}

// ── 테스트 ────────────────────────────────────────────────────────────────

describe('ChatDetailPage — 낙관적 메시지 CSS 클래스', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('_optimistic=true + _rejectedReason 없음 → chat_detail_message_optimistic 클래스 적용', () => {
    const optimistic = makeChatMessage({
      id: 'temp-001' as unknown as number,
      clientMsgId: 'uuid-001',
      _optimistic: true,
      _sentAt: Date.now(),
    });

    const { container } = renderPage([optimistic]);

    expect(container.querySelector('.chat_detail_message_optimistic')).not.toBeNull();
  });

  it('일반 서버 메시지(_optimistic 없음) → optimistic 클래스 없음', () => {
    const normal = makeChatMessage({ id: 1 });

    const { container } = renderPage([normal]);

    expect(container.querySelector('.chat_detail_message_optimistic')).toBeNull();
  });

  it.each([
    ['BLOCKED', 'chat_detail_message_rejected_blocked'],
    ['AUTH', 'chat_detail_message_rejected_auth'],
    ['NETWORK', 'chat_detail_message_rejected_network'],
    ['RATE_LIMIT', 'chat_detail_message_rejected_rate_limit'],
    ['UNKNOWN', 'chat_detail_message_rejected_unknown'],
  ] as const)(
    '_rejectedReason=%s → %s CSS 클래스 적용',
    (reason, expectedClass) => {
      const rejected = makeChatMessage({
        id: 'temp-rej' as unknown as number,
        clientMsgId: 'uuid-rej',
        _optimistic: true,
        _rejectedReason: reason,
      });

      const { container } = renderPage([rejected]);

      expect(container.querySelector(`.${expectedClass}`)).not.toBeNull();
      // rejected 메시지에는 optimistic 클래스 미적용
      expect(container.querySelector('.chat_detail_message_optimistic')).toBeNull();
    },
  );

  it('_rejectedReason 있는 메시지는 chat_detail_message_rejected 베이스 클래스도 가짐', () => {
    const rejected = makeChatMessage({
      id: 'temp-rej2' as unknown as number,
      _optimistic: true,
      _rejectedReason: 'NETWORK',
    });

    const { container } = renderPage([rejected]);

    expect(container.querySelector('.chat_detail_message_rejected')).not.toBeNull();
  });
});
