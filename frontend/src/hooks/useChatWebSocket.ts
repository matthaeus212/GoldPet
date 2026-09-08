/**
 * React hook for WebSocket chat integration
 */

import { useEffect, useCallback, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { chatWebSocket } from '../services/websocket/chatWebSocket';
import { chatService } from '../services/chatService';
import type { ChatMessage, ChatRoom } from '../services/chatService';
import { chatMetrics } from '../utils/chatMetrics';

type DeletedEvent = { type: 'MESSAGE_DELETED'; messageId: string | number };

interface UseChatWebSocketOptions {
  roomId: string | undefined;
  currentUserId?: number;
  enabled?: boolean;
}

export function useChatWebSocket({ roomId, currentUserId, enabled = true }: UseChatWebSocketOptions) {
  const queryClient = useQueryClient();
  const isConnectedRef = useRef(false);
  const [typingUsers, setTypingUsers] = useState<Set<number>>(new Set());
  const typingTimeoutRef = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  // Handle incoming WebSocket messages
  const handleMessage = useCallback(
    (message: ChatMessage | DeletedEvent) => {
      // Handle MESSAGE_DELETED events from WebSocket
      if ((message as DeletedEvent).type === 'MESSAGE_DELETED') {
        const deletedId = (message as DeletedEvent).messageId;
        queryClient.setQueryData(
          ['chatMessages', roomId],
          (oldMessages: ChatMessage[] | undefined) => {
            if (!oldMessages) return oldMessages;
            return oldMessages.map((msg) =>
              String(msg.id) === String(deletedId)
                ? { ...msg, deletedAt: new Date().toISOString(), textContent: null }
                : msg
            );
          }
        );
        return;
      }

      const chatMessage = message as ChatMessage;

      queryClient.setQueryData(
        ['chatMessages', roomId],
        (oldMessages: ChatMessage[] | undefined) => {
          if (!oldMessages) return [chatMessage];

          // Self-echo matching: replace optimistic row by clientMsgId
          if (chatMessage.clientMsgId) {
            const optIdx = oldMessages.findIndex(
              (m) => m.clientMsgId === chatMessage.clientMsgId && m._optimistic
            );
            if (optIdx !== -1) {
              const optimistic = oldMessages[optIdx];
              // Record RTT for text messages
              if (optimistic.type === 'text' && optimistic._sentAt) {
                chatMetrics.recordRtt(chatMessage.clientMsgId, Date.now() - optimistic._sentAt, roomId!);
              }
              // Record upload duration for image messages (upload start → server echo)
              if (optimistic.type === 'image' && optimistic._sentAt) {
                chatMetrics.recordImageUpload(chatMessage.clientMsgId, Date.now() - optimistic._sentAt, roomId!);
              }
              const updated = [...oldMessages];
              updated[optIdx] = chatMessage;
              return updated;
            }
          }

          // Avoid duplicates by checking ID
          if (oldMessages.some((m) => String(m.id) === String(chatMessage.id))) {
            return oldMessages;
          }
          return [...oldMessages, chatMessage];
        }
      );

      // Mark as read and patch lastMessage in cache (avoid full chatRooms refetch).
      // Debounced to collapse rapid WebSocket bursts (~10 msgs/s) into a single
      // POST /chat/rooms/{roomId}/read after 300ms of quiet.
      if (roomId) {
        chatService.markAsReadDebounced(roomId);
        queryClient.setQueryData(['chatRooms'], (old: ChatRoom[] | undefined) => {
          if (!old) return old;
          return old.map((room) =>
            String(room.id) === String(roomId)
              ? {
                  ...room,
                  lastMessage: chatMessage.textContent || (chatMessage.type === 'image' ? '[이미지]' : chatMessage.type === 'emoticon' ? '[이모티콘]' : '[파일]'),
                  lastMessageTime: chatMessage.timestamp || chatMessage.createdAt || null,
                  unreadCount: 0,
                }
              : room
          );
        });
      }
    },
    [queryClient, roomId]
  );

  // Handle read receipt events with server-computed unreadCounts
  const handleReadUpdate = useCallback(
    (event: { type: string; userId: number; updates?: Array<{ messageId: number; unreadCount: number }> }) => {
      if (!roomId) return;

      if (event.updates && event.updates.length > 0) {
        // Build a map of messageId -> server-computed unreadCount
        const updateMap = new Map(
          event.updates.map((u) => [Number(u.messageId), u.unreadCount])
        );

        queryClient.setQueryData(
          ['chatMessages', roomId],
          (oldMessages: ChatMessage[] | undefined) => {
            if (!oldMessages) return oldMessages;
            return oldMessages.map((msg) => {
              const newCount = updateMap.get(Number(msg.id));
              if (newCount !== undefined) {
                return { ...msg, unreadCount: newCount };
              }
              return msg;
            });
          }
        );
      }

      // Also invalidate chat rooms to update room-level unread count
      queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
    },
    [queryClient, roomId]
  );

  // Handle typing events
  const handleTyping = useCallback(
    (userId: number, isTyping: boolean) => {
      // Ignore own typing events
      if (userId === currentUserId) return;

      // Clear existing timeout for this user
      const existingTimeout = typingTimeoutRef.current.get(userId);
      if (existingTimeout) {
        clearTimeout(existingTimeout);
        typingTimeoutRef.current.delete(userId);
      }

      if (isTyping) {
        setTypingUsers(prev => new Set(prev).add(userId));
        // Auto-clear typing after 1.5 seconds of no updates
        const timeout = setTimeout(() => {
          setTypingUsers(prev => {
            const newSet = new Set(prev);
            newSet.delete(userId);
            return newSet;
          });
          typingTimeoutRef.current.delete(userId);
        }, 1500);
        typingTimeoutRef.current.set(userId, timeout);
      } else {
        setTypingUsers(prev => {
          const newSet = new Set(prev);
          newSet.delete(userId);
          return newSet;
        });
      }
    },
    [currentUserId]
  );

  // Connect and subscribe to room
  useEffect(() => {
    if (!roomId || !enabled) return;

    const connectAndSubscribe = async () => {
      try {
        await chatWebSocket.connect();
        chatWebSocket.subscribeToRoom(roomId, handleMessage);
        chatWebSocket.subscribeToTyping(roomId, handleTyping);
        chatWebSocket.subscribeToTopic(`/topic/chat/${roomId}/read`, (data: unknown) => {
          handleReadUpdate(data as { type: string; userId: number; lastReadAt: string });
        });
        isConnectedRef.current = true;
      } catch (error) {
        console.error('[useChatWebSocket] Connection failed:', error);
        isConnectedRef.current = false;
      }
    };

    connectAndSubscribe();

    const timeouts = typingTimeoutRef.current;
    return () => {
      if (roomId) {
        chatWebSocket.unsubscribeFromRoom(roomId);
        chatWebSocket.unsubscribeFromTyping(roomId);
        chatWebSocket.unsubscribeFromTopic(`/topic/chat/${roomId}/read`);
        // Clear all typing timeouts
        timeouts.forEach(timeout => clearTimeout(timeout));
        timeouts.clear();
      }
    };
  }, [roomId, enabled, handleMessage, handleTyping, handleReadUpdate]);

  // Send message via WebSocket
  const sendMessage = useCallback(
    (senderId: number, content: string, replyToId?: number) => {
      if (!roomId) return;

      if (chatWebSocket.connected) {
        chatWebSocket.sendMessage(roomId, senderId, content, replyToId);
      } else {
        console.warn('[useChatWebSocket] Not connected, message not sent via WebSocket');
      }
    },
    [roomId]
  );

  // Send typing event
  const sendTypingEvent = useCallback(
    (isTyping: boolean) => {
      if (!roomId || !currentUserId) return;
      chatWebSocket.sendTypingEvent(roomId, currentUserId, isTyping);
    },
    [roomId, currentUserId]
  );

  return {
    sendMessage,
    sendTypingEvent,
    typingUsers,
    isConnected: chatWebSocket.connected,
  };
}
