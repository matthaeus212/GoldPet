/**
 * Native Bridge 클라이언트
 * Flutter WebView와 통신하는 JavaScript Bridge 구현
 */

import { logger } from '../utils/logger';
import type { BridgeMessage, BridgeResponse, NativeEventType, NativeMethod } from './bridgeTypes';

interface PendingRequest {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
  timeout: ReturnType<typeof setTimeout>;
}

class NativeBridge {
  private pendingRequests = new Map<string, PendingRequest>();
  private eventListeners = new Map<string, Set<(data: unknown) => void>>();
  private isInitialized = false;
  // Buffer for one-shot events fired before listeners are registered
  private eventBuffer = new Map<string, { data: unknown; timestamp: number }[]>();
  private static readonly BUFFERABLE_EVENTS: Set<string> = new Set([
    'pushNotificationClicked',
    'authExpired',
    'tokenSync',
    'walkCompleted',
    'deviceRegistered',
  ]);
  private static readonly BUFFER_TTL_MS = 10_000; // 10 seconds

  constructor() {
    this.setupBridge();
  }

  private setupBridge() {
    // Flutter WebView에서 호출할 수 있는 전역 함수 등록
    // Flutter WebView에서 호출할 수 있는 전역 함수 등록
    // 기존 window.nativeBridge가 있으면 handleResponse만 연결하고, 없으면 생성
    const existingBridge = (window as unknown as Record<string, unknown>).nativeBridge || {};
    (window as unknown as Record<string, unknown>).nativeBridge = {
      ...existingBridge,
      handleResponse: (response: BridgeResponse) => {
        this.handleResponse(response);
      },
    };

    // 네이티브 이벤트 리스너
    window.addEventListener('nativeEvent', ((e: CustomEvent) => {
      const { type, data } = e.detail;
      this.emitEvent(type, data);
    }) as EventListener);

    // Flutter WebView의 window.postMessage() 수신
    // WebView에서 runJavaScript()로 주입된 스크립트는 페이지 origin과 동일하거나 'null'
    const expectedOrigin = window.location.origin;
    window.addEventListener('message', (e: MessageEvent) => {
      // origin 검증: same-origin 또는 WebView 내부 'null' origin만 허용
      if (e.origin && e.origin !== 'null' && e.origin !== expectedOrigin) {
        console.warn('[NativeBridge] Rejected message from unknown origin:', e.origin);
        return;
      }
      // Flutter sendEventToReact 형식: { type: eventName, data: {...} }
      if (e.data && typeof e.data === 'object' && e.data.type) {
        this.emitEvent(e.data.type, e.data.data);
      }
    });

    this.isInitialized = true;
    logger.debug('NativeBridge initialized');
  }

  /**
   * ARCH-001: method 를 `string` 으로 받으면 Flutter 가 모르는 메서드를 호출해도 컴파일타임에
   * 걸리지 않는다(계약 drift 가 조용히 샌다). SSoT 유니온으로 좁혀 강제한다.
   */
  async callMethod(method: NativeMethod, params?: Record<string, unknown>): Promise<unknown> {
    if (!this.isInitialized) {
      throw new Error('NativeBridge not initialized');
    }

    const id = this.generateId();
    const message: BridgeMessage = {
      id,
      method,
      params,
      timestamp: Date.now(),
    };

    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pendingRequests.delete(id);
        reject(new Error(`Bridge call timeout: ${method}`));
      }, 30000);

      this.pendingRequests.set(id, { resolve, reject, timeout });

      // Flutter WebView의 JavaScript Channel 호출
      if ((window as unknown as Record<string, unknown>).NativeBridge) {
        ((window as unknown as Record<string, unknown>).NativeBridge as { postMessage: (msg: string) => void }).postMessage(JSON.stringify(message));
      } else {
        clearTimeout(timeout);
        reject(new Error('NativeBridge channel not available'));
      }
    });
  }

  private handleResponse(response: BridgeResponse) {
    const pending = this.pendingRequests.get(response.id);
    if (!pending) {
      console.warn('No pending request found for id:', response.id);
      return;
    }

    clearTimeout(pending.timeout);
    this.pendingRequests.delete(response.id);

    if (response.success) {
      pending.resolve(response.data);
    } else {
      pending.reject(new Error(response.error?.message || 'Unknown error'));
    }
  }

  onEvent(eventType: NativeEventType, callback: (data: unknown) => void) {
    if (!this.eventListeners.has(eventType)) {
      this.eventListeners.set(eventType, new Set());
    }
    this.eventListeners.get(eventType)!.add(callback);

    // Replay buffered events (e.g. pushNotificationClicked fired before React mounted)
    const buffered = this.eventBuffer.get(eventType);
    if (buffered && buffered.length > 0) {
      const now = Date.now();
      const valid = buffered.filter((e) => now - e.timestamp < NativeBridge.BUFFER_TTL_MS);
      this.eventBuffer.delete(eventType);
      valid.forEach((e) => {
        queueMicrotask(() => callback(e.data));
      });
    }

    return () => {
      this.eventListeners.get(eventType)?.delete(callback);
    };
  }

  private emitEvent(eventType: string, data: unknown) {
    const listeners = this.eventListeners.get(eventType);
    if (listeners && listeners.size > 0) {
      listeners.forEach((callback) => callback(data));
    } else if (NativeBridge.BUFFERABLE_EVENTS.has(eventType)) {
      // No listeners yet — buffer for later replay
      if (!this.eventBuffer.has(eventType)) {
        this.eventBuffer.set(eventType, []);
      }
      this.eventBuffer.get(eventType)!.push({ data, timestamp: Date.now() });
    }
  }

  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  isAvailable(): boolean {
    return this.isInitialized && !!(window as unknown as Record<string, unknown>).NativeBridge;
  }
}

export const nativeBridge = new NativeBridge();

