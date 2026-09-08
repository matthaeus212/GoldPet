// Chat Service - API integration for Chat features

import apiClient from './api/client';
import { typedClient } from './api/typedClient';
import type { ChatRoomType, FileInfo } from '../types/api';

// T-chat-latency-v2 Step 4 — markAsRead 호출 디바운스.
// WebSocket 새 메시지 수신마다 즉시 POST /chat/rooms/{roomId}/read 를 치면
// 다중 메시지 버스트(연속 수신 10건) 시 10회 호출 → 300ms 디바운스로 1회로 합침.
// roomId 별 타이머를 분리해 다른 방 읽음 처리는 서로 간섭하지 않음.
export const MARK_AS_READ_DEBOUNCE_MS = 300;
const markAsReadTimers = new Map<string, ReturnType<typeof setTimeout>>();

export interface ChatRoom {
  id: number;
  name: string;
  lastMessage: string | null;
  lastMessageTime: string | null;
  unreadCount: number;
  participants: {
    id: number;
    nickname: string;
    profileImageUrl?: string;
  }[];
  isGroup: boolean;
  // Sourced from OpenAPI schema via types/api.ts; AI_PET added in Phase 3.3.
  roomType?: ChatRoomType;
}

// Generated alias (Phase 4 sweep).
export type { FileInfo };

export interface ChatMessage {
  id: number;
  roomId: number;
  senderId: number;
  senderNickname: string;
  textContent: string;
  timestamp: string;
  type: 'text' | 'image' | 'video' | 'file' | 'system' | 'emoticon' | 'emoji';
  // API response fields
  createdAt?: string;
  messageType?: string;
  fileId?: number;
  file?: FileInfo;
  emoticonId?: number;
  emoticonImageUrl?: string;
  emoticonImageUrlThumbnail?: string;
  emoticonImageUrlViewer?: string;
  emoticonName?: string;
  // Reply fields
  replyToId?: number;
  replyToSenderNickname?: string;
  replyToTextContent?: string;
  replyToMessageType?: string;
  replyToFileUrl?: string;
  replyToFileName?: string;
  replyToEmoticonImageUrl?: string;
  replyToEmoticonImageUrlThumbnail?: string;
  replyToEmoticonImageUrlViewer?: string;
  // Soft delete
  deletedAt?: string;
  // Read receipt
  unreadCount?: number;
  // Echoed by server; used to match optimistic rows to confirmed rows
  clientMsgId?: string;
  // Client-only optimistic UI fields (never sent to server)
  _optimistic?: boolean;
  _rejectedReason?: 'BLOCKED' | 'AUTH' | 'NETWORK' | 'RATE_LIMIT' | 'UNKNOWN';
  _sentAt?: number;
}

export interface ChatRequestItem {
  id: number;
  requesterNickname: string;
  requesterProfileImage?: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  createdAt: string;
}

// Error thrown by getOrCreateDirectRoom on failure. `code` mirrors the backend's
// ErrorResponse.errorCode (e.g. 'NO_MATCH', 'FORBIDDEN') so callers can branch without
// parsing `message`. Both are additive — `message` keeps its historical fixed string.
export interface ChatServiceError extends Error {
  code?: string;
  status?: number;
}

export const chatService = {
  getChatRooms: async (): Promise<ChatRoom[]> => {
    const response = await typedClient.get('/api/v1/chat/rooms');
    return response.data as unknown as ChatRoom[];
  },

  getMessages: async (roomId: string, page = 0): Promise<ChatMessage[]> => {
    const response = await typedClient.getPath(
      '/api/v1/chat/rooms/{roomId}/messages',
      { roomId },
      { params: { page } },
    );
    const data = response.data as { content?: ChatMessage[] } | ChatMessage[];
    const messages: ChatMessage[] = Array.isArray(data) ? data : (data.content ?? []);

    // Batch-resolve presigned/CDN URLs via resolveMany (single round-trip)
    const fileIds = new Set<number>();
    for (const msg of messages) {
      if (msg.file?.id) fileIds.add(msg.file.id);
      if (msg.fileId) fileIds.add(msg.fileId);
    }

    if (fileIds.size > 0) {
      try {
        type ResolvedAttachment = { id: number; url: string; thumbnailUrl?: string; mediumUrl?: string; viewerUrl?: string; fileType: string; mimeType: string };
        const res = await apiClient.post<{ attachments: Record<string, ResolvedAttachment> }>(
          '/files/resolve',
          { ids: Array.from(fileIds) },
        );
        const attachments = res.data.attachments;
        for (const msg of messages) {
          if (msg.file?.id) {
            const resolved = attachments[String(msg.file.id)];
            if (resolved) msg.file = { ...msg.file, url: resolved.url };
          }
        }
      } catch { /* ignore resolution failures — server URLs remain as-is */ }
    }

    return messages;
  },

  sendMessage: async (
    roomId: string,
    content: string | null,
    options?: {
      messageType?: 'TEXT' | 'IMAGE' | 'VIDEO' | 'FILE' | 'EMOTICON';
      fileId?: number;
      emoticonId?: number;
      replyToId?: number;
      clientMsgId?: string;
    }
  ): Promise<ChatMessage> => {
    try {
      const response = await typedClient.postPath(
        '/api/v1/chat/rooms/{roomId}/messages',
        { roomId },
        {
          content: content ?? undefined,
          messageType: options?.messageType ?? 'TEXT',
          fileId: options?.fileId,
          emoticonId: options?.emoticonId,
          replyToId: options?.replyToId,
          clientMsgId: options?.clientMsgId,
        } as Parameters<typeof typedClient.postPath<'/api/v1/chat/rooms/{roomId}/messages'>>[2],
      );
      return response.data as unknown as ChatMessage;
    } catch {
      throw new Error('메시지 전송에 실패했습니다.');
    }
  },

  uploadFile: async (file: File, onUploadProgress?: (pct: number) => void): Promise<{ id: number; url: string; fileType: string; mimeType: string }> => {
    try {
      if (file.type.startsWith('image/')) {
        const { compressImage } = await import('../utils/imageCompression');
        file = await compressImage(file);
      }
      const formData = new FormData();
      formData.append('file', file);
      formData.append('category', 'chat');

      // Multipart — stays on apiClient; typedClient is JSON-only.
      const response = await apiClient.post('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: onUploadProgress
          ? (evt) => {
              if (evt.total) onUploadProgress(Math.round((evt.loaded / evt.total) * 100));
            }
          : undefined,
      });
      return response.data;
    } catch {
      throw new Error('파일 업로드에 실패했습니다.');
    }
  },

  createRoom: async (participantIds: number[], name?: string): Promise<ChatRoom> => {
    try {
      const response = await typedClient.post('/api/v1/chat/rooms', {
        roomType: 'GROUP',
        title: name,
        participantUserIds: participantIds,
      } as unknown as Parameters<typeof typedClient.post<'/api/v1/chat/rooms'>>[1]);
      return response.data as unknown as ChatRoom;
    } catch {
      throw new Error('채팅방 생성에 실패했습니다.');
    }
  },

  markAsRead: async (roomId: string): Promise<void> => {
    try {
      await typedClient.postPath(
        '/api/v1/chat/rooms/{roomId}/read',
        { roomId },
        undefined,
      );
    } catch {
      // Ignore read marking errors
    }
  },

  /**
   * Debounced wrapper for `markAsRead` — collapses rapid-fire invocations
   * (e.g. WebSocket message bursts) into a single HTTP call after
   * `MARK_AS_READ_DEBOUNCE_MS` of quiet. Per-room timers prevent interference
   * between concurrent chat rooms.
   */
  markAsReadDebounced: (
    roomId: string,
    delayMs: number = MARK_AS_READ_DEBOUNCE_MS,
  ): void => {
    const existing = markAsReadTimers.get(roomId);
    if (existing) clearTimeout(existing);
    const timer = setTimeout(() => {
      markAsReadTimers.delete(roomId);
      void chatService.markAsRead(roomId);
    }, delayMs);
    markAsReadTimers.set(roomId, timer);
  },

  /** Cancels any pending debounced markAsRead call for the given room. */
  cancelMarkAsReadDebounced: (roomId: string): void => {
    const existing = markAsReadTimers.get(roomId);
    if (existing) {
      clearTimeout(existing);
      markAsReadTimers.delete(roomId);
    }
  },

  leaveRoom: async (roomId: string): Promise<void> => {
    try {
      await typedClient.deletePath(
        '/api/v1/chat/rooms/{roomId}/leave',
        { roomId },
      );
    } catch {
      throw new Error('채팅방 나가기에 실패했습니다.');
    }
  },

  inviteToRoom: async (roomId: number, userId: number): Promise<{ roomType: string }> => {
    try {
      const response = await typedClient.postPath(
        '/api/v1/chat/rooms/{roomId}/invite',
        { roomId },
        { userId } as unknown as Parameters<typeof typedClient.postPath<'/api/v1/chat/rooms/{roomId}/invite'>>[2],
      );
      return response.data as unknown as { roomType: string };
    } catch {
      throw new Error('초대에 실패했습니다.');
    }
  },

  /**
   * Get or create a direct chat room with a matched user.
   * Returns the chat room (existing or newly created).
   */
  getOrCreateDirectRoom: async (targetUserId: number): Promise<ChatRoom> => {
    try {
      const response = await typedClient.postPath(
        '/api/v1/chat/rooms/direct/{targetUserId}',
        { targetUserId },
        undefined,
      );
      return response.data as unknown as ChatRoom;
    } catch (error: unknown) {
      const err = error as { response?: { status?: number; data?: { errorCode?: string } } };
      const status = err.response?.status;
      const message = status === 400 ? '매칭된 상대가 아닙니다.' : '채팅방 생성에 실패했습니다.';
      // errorCode(NO_MATCH/FORBIDDEN 등)와 status를 가산적으로 부착 — message 문자열은 그대로 보존해
      // 기존 소비자(문구 고정 테스트 등)와 후방호환을 유지하면서, 호출자가 케이스별로 분기할 수 있게 한다.
      throw Object.assign(new Error(message), {
        code: err.response?.data?.errorCode,
        status,
      }) as ChatServiceError;
    }
  },

  getChatRequests: async (): Promise<ChatRequestItem[]> =>
    typedClient.get('/api/v1/chat/rooms/requests').then(r => r.data as unknown as ChatRequestItem[]),

  acceptChatRequest: async (requestId: number): Promise<ChatRoom> =>
    typedClient
      .postPath('/api/v1/chat/rooms/requests/{requestId}/accept', { requestId }, undefined)
      .then(r => r.data as unknown as ChatRoom),

  rejectChatRequest: async (requestId: number): Promise<void> => {
    await typedClient.postPath(
      '/api/v1/chat/rooms/requests/{requestId}/reject',
      { requestId },
      undefined,
    );
  },

  deleteChatRequest: async (requestId: number): Promise<void> => {
    await typedClient.deletePath(
      '/api/v1/chat/rooms/requests/{requestId}',
      { requestId },
    );
  },

  deleteMessage: async (roomId: string, messageId: number): Promise<void> => {
    await typedClient.deletePath(
      '/api/v1/chat/rooms/{roomId}/messages/{messageId}',
      { roomId, messageId },
    );
  },
};
