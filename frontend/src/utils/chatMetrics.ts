/**
 * Chat latency metrics utility (Task #2 — Step 0 계측)
 *
 * - console.group: 항상 켜짐 (모든 환경)
 * - POST /api/v1/metrics/chat: VITE_CHAT_METRICS_ENABLED=true + dev/local 환경에서만 전송
 * - 전송 실패 시 silent (에러 throw 금지)
 */

import { logger } from './logger';
import apiClient from '../services/api/client';

// Allowed metric names
export type ChatMetricName =
  | 'chat.send.rtt_ms'
  | 'chat.image.upload_ms'
  | 'chat.list.open_to_first_paint_ms';

export interface ChatMetricPayload {
  metric: ChatMetricName;
  clientMsgId: string;
  durationMs: number;
  roomId: string | number;
}

const METRICS_ENABLED = import.meta.env.VITE_CHAT_METRICS_ENABLED === 'true';
// VITE_ENV is explicitly set per env file: 'local' | 'dev' | 'prod'
const IS_DEV_OR_LOCAL =
  import.meta.env.VITE_ENV === 'dev' || import.meta.env.VITE_ENV === 'local';

async function send(payload: ChatMetricPayload): Promise<void> {
  // console.group is always on
  console.group(`[chatMetrics] ${payload.metric}`);
  logger.debug('clientMsgId :', payload.clientMsgId);
  logger.debug('durationMs  :', payload.durationMs);
  logger.debug('roomId      :', payload.roomId);
  console.groupEnd();

  if (!METRICS_ENABLED || !IS_DEV_OR_LOCAL) return;

  try {
    await apiClient.post('/metrics/chat', payload);
  } catch {
    // silent — backend endpoint (Task #1) may not exist yet
  }
}

export const chatMetrics = {
  /** 텍스트 메시지 round-trip 시간 (낙관적 send → self-echo 수신) */
  recordRtt(clientMsgId: string, durationMs: number, roomId: string | number): void {
    void send({ metric: 'chat.send.rtt_ms', clientMsgId, durationMs, roomId });
  },

  /** 이미지 업로드 완료 시간 (blob 선표시 → 서버 URL swap) */
  recordImageUpload(clientMsgId: string, durationMs: number, roomId: string | number): void {
    void send({ metric: 'chat.image.upload_ms', clientMsgId, durationMs, roomId });
  },

  /** 채팅방 open → 첫 메시지 렌더 시간 */
  recordListOpen(roomId: string | number, durationMs: number): void {
    void send({ metric: 'chat.list.open_to_first_paint_ms', clientMsgId: '', durationMs, roomId });
  },
};
