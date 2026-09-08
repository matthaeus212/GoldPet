/**
 * WebSocket service for real-time chat messaging using STOMP over SockJS
 */

import { logger } from '../../utils/logger';
import { Client } from '@stomp/stompjs';
import type { IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { ChatMessage } from '../chatService';
import { useAuthStore } from '../../stores/authStore';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';
const WS_URL = `${API_BASE_URL}/ws`;

type DeletedEvent = { type: 'MESSAGE_DELETED'; messageId: string | number };
type MessageHandler = (message: ChatMessage | DeletedEvent) => void;

class ChatWebSocketService {
  private client: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private messageHandlers: Map<string, MessageHandler[]> = new Map();
  private isConnected = false;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private connectPromise: Promise<void> | null = null;

  connect(): Promise<void> {
    // Return existing connection promise if already connecting
    if (this.connectPromise) {
      return this.connectPromise;
    }

    // Already connected
    if (this.isConnected && this.client?.connected) {
      return Promise.resolve();
    }

    this.connectPromise = new Promise((resolve, reject) => {

      const token = useAuthStore.getState().token;

      this.client = new Client({
        webSocketFactory: () => new SockJS(WS_URL),
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        debug: (str) => {
          if (import.meta.env.DEV) {
            console.log('[WebSocket Debug]', str);
          }
        },
        reconnectDelay: this.reconnectDelay,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          logger.debug('[WebSocket] Connected');
          this.isConnected = true;
          this.reconnectAttempts = 0;
          this.connectPromise = null;
          resolve();
        },
        onDisconnect: () => {
          logger.debug('[WebSocket] Disconnected');
          this.isConnected = false;
        },
        onStompError: (frame) => {
          console.error('[WebSocket] STOMP Error:', frame.headers['message']);
          this.connectPromise = null;
          reject(new Error(frame.headers['message']));
        },
        onWebSocketError: (event) => {
          console.error('[WebSocket] WebSocket Error:', event);
          this.reconnectAttempts++;
          if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            this.connectPromise = null;
            reject(new Error('WebSocket connection failed after max attempts'));
          }
        },
      });

      this.client.activate();
    });

    return this.connectPromise;
  }

  disconnect(): void {
    if (this.client) {
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions.clear();
      this.messageHandlers.clear();
      this.client.deactivate();
      this.client = null;
      this.isConnected = false;
    }
  }

  subscribeToRoom(roomId: string, onMessage: MessageHandler): void {
    if (!this.client || !this.isConnected) {
      console.warn('[WebSocket] Not connected. Call connect() first.');
      return;
    }

    const destination = `/topic/chat/${roomId}`;

    // Check if already subscribed
    if (this.subscriptions.has(roomId)) {
      // Just add the handler
      const handlers = this.messageHandlers.get(roomId) || [];
      handlers.push(onMessage);
      this.messageHandlers.set(roomId, handlers);
      return;
    }

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const data = JSON.parse(message.body);
        const handlers = this.messageHandlers.get(roomId) || [];
        if (data.type === 'MESSAGE_DELETED') {
          handlers.forEach((handler) =>
            handler({ type: 'MESSAGE_DELETED', messageId: data.messageId } as DeletedEvent)
          );
          return;
        }
        const chatMessage = this.parseMessage(message.body);
        handlers.forEach((handler) => handler(chatMessage));
      } catch (error) {
        console.error('[WebSocket] Failed to parse message:', error);
      }
    });

    this.subscriptions.set(roomId, subscription);
    this.messageHandlers.set(roomId, [onMessage]);
  }

  unsubscribeFromRoom(roomId: string): void {
    const subscription = this.subscriptions.get(roomId);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(roomId);
      this.messageHandlers.delete(roomId);
    }
  }

  /**
   * Subscribe to a generic topic (e.g., user-specific notifications)
   */
  subscribeToTopic(topic: string, onMessage: (data: unknown) => void): void {
    if (!this.client || !this.isConnected) {
      console.warn('[WebSocket] Not connected. Call connect() first.');
      return;
    }

    // Check if already subscribed
    if (this.subscriptions.has(topic)) {
      return;
    }

    const subscription = this.client.subscribe(topic, (message: IMessage) => {
      try {
        const data = JSON.parse(message.body);
        onMessage(data);
      } catch (error) {
        console.error('[WebSocket] Failed to parse message:', error);
      }
    });

    this.subscriptions.set(topic, subscription);
  }

  unsubscribeFromTopic(topic: string): void {
    const subscription = this.subscriptions.get(topic);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(topic);
    }
  }

  sendMessage(roomId: string, senderId: number, content: string, replyToId?: number, clientMsgId?: string): void {
    if (!this.client || !this.isConnected) {
      console.error('[WebSocket] Not connected. Cannot send message.');
      return;
    }

    const message: Record<string, unknown> = {
      roomId: parseInt(roomId, 10),
      senderId,
      messageType: 'TEXT',
      textContent: content,
    };

    if (replyToId != null) message.replyToId = replyToId;
    if (clientMsgId) message.clientMsgId = clientMsgId;

    this.client.publish({
      destination: '/app/chat.sendMessage',
      body: JSON.stringify(message),
    });
  }

  sendTypingEvent(roomId: string, userId: number, isTyping: boolean): void {
    if (!this.client || !this.isConnected) {
      return;
    }

    this.client.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify({
        roomId: parseInt(roomId, 10),
        userId,
        isTyping,
      }),
    });
  }

  subscribeToTyping(roomId: string, onTyping: (userId: number, isTyping: boolean) => void): void {
    if (!this.client || !this.isConnected) {
      console.warn('[WebSocket] Not connected. Call connect() first.');
      return;
    }

    const destination = `/topic/chat/${roomId}/typing`;
    const subscriptionKey = `typing-${roomId}`;

    if (this.subscriptions.has(subscriptionKey)) {
      return;
    }

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const data = JSON.parse(message.body);
        onTyping(data.userId, data.isTyping);
      } catch (error) {
        console.error('[WebSocket] Failed to parse typing event:', error);
      }
    });

    this.subscriptions.set(subscriptionKey, subscription);
  }

  unsubscribeFromTyping(roomId: string): void {
    const subscriptionKey = `typing-${roomId}`;
    const subscription = this.subscriptions.get(subscriptionKey);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(subscriptionKey);
    }
  }

  private parseMessage(body: string): ChatMessage {
    const data = JSON.parse(body);
    return {
      id: data.id || 0,
      roomId: data.roomId || 0,
      senderId: data.senderId || 0,
      senderNickname: data.senderNickname || '',
      textContent: data.textContent || '',
      timestamp: data.createdAt || data.timestamp || new Date().toISOString(),
      type: this.mapMessageType(data.messageType || data.type),
      // Include additional fields for file attachments
      createdAt: data.createdAt,
      messageType: data.messageType,
      fileId: data.fileId,
      file: data.file ? {
        id: data.file.id,
        url: data.file.url,
        fileType: data.file.fileType,
        mimeType: data.file.mimeType,
        sizeBytes: data.file.sizeBytes,
        width: data.file.width,
        height: data.file.height,
      } : undefined,
      emoticonId: data.emoticonId,
      emoticonImageUrl: data.emoticonImageUrl,
      emoticonName: data.emoticonName,
      replyToId: data.replyToId,
      replyToSenderNickname: data.replyToSenderNickname,
      replyToTextContent: data.replyToTextContent,
      unreadCount: data.unreadCount,
      clientMsgId: data.clientMsgId,
    };
  }

  private mapMessageType(type: string): 'text' | 'image' | 'video' | 'file' | 'system' | 'emoticon' | 'emoji' {
    switch (type?.toUpperCase()) {
      case 'IMAGE':
        return 'image';
      case 'VIDEO':
        return 'video';
      case 'FILE':
        return 'file';
      case 'SYSTEM':
        return 'system';
      case 'EMOTICON':
        return 'emoticon';
      case 'EMOJI':
        return 'emoji';
      default:
        return 'text';
    }
  }

  get connected(): boolean {
    return this.isConnected;
  }
}

// Singleton instance
export const chatWebSocket = new ChatWebSocketService();
