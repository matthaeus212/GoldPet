/**
 * React hook for WebSocket chat list notifications
 * Subscribes to user-specific topic to get notified of new messages in any chat room
 */

import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { chatWebSocket } from '../services/websocket/chatWebSocket';
import { useAuthStore } from '../stores/authStore';

interface UseChatListWebSocketOptions {
  enabled?: boolean;
}

/** 알림 버스트를 하나의 목록 재요청으로 합치는 창. 체감 지연 없이 폭주만 막는 값. */
const COALESCE_MS = 300;

export function useChatListWebSocket({ enabled = true }: UseChatListWebSocketOptions = {}) {
  const queryClient = useQueryClient();
  // PERF-014: 이 훅은 user.id 만 쓴다. 스토어 전체를 구독하면 token 갱신마다 이 훅이 딸린
  // 컴포넌트가 리렌더된다.
  const userId = useAuthStore((s) => s.user?.id ?? null);
  const isSubscribedRef = useRef(false);

  useEffect(() => {
    if (!enabled || !userId) return;

    const topic = `/topic/user/${userId}/chats`;

    // PERF-020: 알림 1건마다 chatRooms 전체를 무효화하면, 여러 방에서 메시지가 몰아칠 때
    // 메시지 수만큼 목록 재요청이 나간다. 서버 페이로드가 {roomId, type} 뿐이라 방 단위
    // 부분 패치(lastMessage/unreadCount)는 불가능하므로, 버스트를 한 번의 재요청으로 합친다.
    let coalesceTimer: ReturnType<typeof setTimeout> | null = null;
    const invalidateChatRooms = () => {
      if (coalesceTimer) return;
      coalesceTimer = setTimeout(() => {
        coalesceTimer = null;
        queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
      }, COALESCE_MS);
    };

    const connectAndSubscribe = async () => {
      try {
        await chatWebSocket.connect();

        if (isSubscribedRef.current) return;

        chatWebSocket.subscribeToTopic(topic, invalidateChatRooms);

        isSubscribedRef.current = true;
      } catch (error) {
        console.error('[useChatListWebSocket] Connection failed:', error);
      }
    };

    connectAndSubscribe();

    return () => {
      if (coalesceTimer) clearTimeout(coalesceTimer);
      chatWebSocket.unsubscribeFromTopic(topic);
      isSubscribedRef.current = false;
    };
  }, [enabled, userId, queryClient]);

  return {
    isConnected: chatWebSocket.connected,
  };
}
