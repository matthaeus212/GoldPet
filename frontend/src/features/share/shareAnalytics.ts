import type { ShareTarget, ShareResult } from './types';

type ShareEvent = 'attempt' | 'success' | 'failure';

interface SharePayload {
  event: ShareEvent;
  kind: ShareTarget['kind'];
  timestamp: string;
  status?: ShareResult['status'];
  channel?: string;
  errorCode?: string;
}

const isDev = import.meta.env.DEV;
const ANALYTICS_ENDPOINT = '/api/v1/analytics/share';

function send(payload: SharePayload): void {
  if (isDev) {
    console.debug('[share]', payload.event, payload);
    return;
  }
  try {
    navigator.sendBeacon(ANALYTICS_ENDPOINT, JSON.stringify(payload));
  } catch {
    // fire-and-forget — endpoint absence is non-fatal
  }
}

export function logShareAttempt(target: ShareTarget): void {
  send({
    event: 'attempt',
    kind: target.kind,
    timestamp: new Date().toISOString(),
  });
}

export function logShareSuccess(target: ShareTarget, result: ShareResult): void {
  send({
    event: 'success',
    kind: target.kind,
    timestamp: new Date().toISOString(),
    status: result.status,
    channel: result.channel,
  });
}

export function logShareFailure(target: ShareTarget, errorCode: string): void {
  send({
    event: 'failure',
    kind: target.kind,
    timestamp: new Date().toISOString(),
    errorCode,
  });
}
