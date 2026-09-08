const ENDPOINT_PATH = '/api/v1/client-logs/error';

interface ErrorPayload {
  url: string | null;
  name: string | null;
  message: string | null;
  stack: string | null;
  componentStack?: string | null;
  extra?: Record<string, unknown>;
}

function sendPayload(payload: ErrorPayload): void {
  try {
    const apiBase = (import.meta.env?.VITE_API_BASE_URL as string | undefined) ?? '';
    const url = `${apiBase}${ENDPOINT_PATH}`;
    const body = JSON.stringify(payload);
    if (typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
      const blob = new Blob([body], { type: 'application/json' });
      navigator.sendBeacon(url, blob);
      return;
    }
    void fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
      keepalive: true,
    }).catch(() => {});
  } catch {
    // reporting must never throw
  }
}

export function reportClientError(
  error: Error,
  extra?: { componentStack?: string | null; extra?: Record<string, unknown> },
): void {
  sendPayload({
    url: typeof window !== 'undefined' ? window.location.href : null,
    name: error.name ?? 'Error',
    message: error.message ?? String(error),
    stack: error.stack ?? null,
    componentStack: extra?.componentStack ?? null,
    extra: extra?.extra,
  });
}

let installed = false;

export function installGlobalErrorReporter(): void {
  if (installed || typeof window === 'undefined') return;
  installed = true;

  window.addEventListener('error', (event: ErrorEvent) => {
    const err =
      event.error instanceof Error
        ? event.error
        : new Error(event.message || 'Unknown window.error');
    reportClientError(err, {
      extra: {
        kind: 'window.error',
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno,
      },
    });
  });

  window.addEventListener('unhandledrejection', (event: PromiseRejectionEvent) => {
    const reason = event.reason;
    const err =
      reason instanceof Error
        ? reason
        : new Error(typeof reason === 'string' ? reason : JSON.stringify(reason));
    reportClientError(err, { extra: { kind: 'unhandledrejection' } });
  });
}
