import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import * as Sentry from '@sentry/react';
import App from './App.tsx';
import './index.css';
import 'swiper/css';
import 'swiper/css/pagination';
import 'swiper/css/navigation';
import './assets/css/common.css';
import './styles/styleguide.css';
import './styles/page-layout.css';
import { installGlobalErrorReporter } from './lib/clientErrorReporter';
import { initGA4 } from './lib/analytics';

// Sentry 초기화 — VITE_SENTRY_DSN이 주입된 경우에만 활성화 (빈값/미설정 시 no-op)
if (import.meta.env.VITE_SENTRY_DSN) {
  Sentry.init({
    dsn: import.meta.env.VITE_SENTRY_DSN as string,
    integrations: [Sentry.browserTracingIntegration()],
    tracesSampleRate: import.meta.env.VITE_ENV === 'prod' ? 0.1 : 1.0,
    environment: (import.meta.env.VITE_ENV as string) || 'local',
    release: import.meta.env.VITE_APP_VERSION as string | undefined,
  });
}

installGlobalErrorReporter();

// GA4 초기화 — VITE_GA_ID가 주입된 경우에만 활성화 (빈값/미설정 시 no-op)
if (import.meta.env.VITE_GA_ID) {
  initGA4();
}

// 새 배포 후 기존 WebView 세션이 가진 구버전 chunk 해시를 참조하다가
// Vite의 동적 import가 404로 실패할 때 자동으로 최신 index.html 재요청 → 새 chunk 로드.
// 무한 루프 방지를 위해 최근 5초 안에 한 번만 reload 허용.
window.addEventListener('vite:preloadError', () => {
  const RELOAD_KEY = '__wpv_preload_reload_at';
  try {
    const last = Number(sessionStorage.getItem(RELOAD_KEY) ?? '0');
    if (Date.now() - last < 5000) return;
    sessionStorage.setItem(RELOAD_KEY, String(Date.now()));
  } catch { /* ignore */ }
  window.location.reload();
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
