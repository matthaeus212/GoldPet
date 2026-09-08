import { typedClient } from './typedClient';
import type {
  PageResponse,
  ChatRoomAdminResponse,
  ChatMessageAdminResponse,
} from '../types/api';

// Generated aliases (Phase 3.11.3).
export type ChatRoomItem = ChatRoomAdminResponse;
export type ChatMessageItem = ChatMessageAdminResponse;

export const chatManagementService = {
  async getChatRooms(params: { page?: number; size?: number }): Promise<PageResponse<ChatRoomItem>> {
    const response = await typedClient.get('/api/v1/admin/chat/rooms', { params });
    return response.data as unknown as PageResponse<ChatRoomItem>;
  },

  async getRoomMessages(roomId: number, page = 0): Promise<PageResponse<ChatMessageItem>> {
    const response = await typedClient.getPath(
      '/api/v1/admin/chat/rooms/{roomId}/messages',
      { roomId },
      { params: { page } },
    );
    return response.data as unknown as PageResponse<ChatMessageItem>;
  },

  async deleteMessage(messageId: number): Promise<void> {
    await typedClient.deletePath(
      '/api/v1/admin/chat/messages/{messageId}',
      { messageId },
    );
  },

  async deleteRoom(roomId: number): Promise<void> {
    await typedClient.deletePath('/api/v1/admin/chat/rooms/{roomId}', { roomId });
  },
};
