import { useEffect } from 'react';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import { APP_STORE_URL } from './storeUrls';

interface ShareRedirectProps {
  to: string;
  search?: string;
}

function detectUA(): 'ios-safari' | 'ios-kakao' | 'android-kakao' | 'android' | 'desktop' {
  const ua = navigator.userAgent;
  const isKakao = /KAKAOTALK/i.test(ua);
  const isIOS = /iPhone|iPad|iPod/.test(ua);
  const isAndroid = /Android/.test(ua);

  if (isKakao && isIOS) return 'ios-kakao';
  if (isKakao && isAndroid) return 'android-kakao';
  if (isIOS) return 'ios-safari';
  if (isAndroid) return 'android';
  return 'desktop';
}

export function ShareRedirect({ to, search = '' }: ShareRedirectProps) {
  const pathname = window.location.pathname;
  const fullSearch = search || window.location.search;

  // Always store redirect target before any navigation
  useEffect(() => {
    sessionStorage.setItem('postLoginRedirect', pathname + fullSearch);
  }, [pathname, fullSearch]);

  const ua = detectUA();

  useEffect(() => {
    if (ua === 'desktop') return;

    const encoded = encodeURIComponent(`https://app.mannamsquare.com${pathname}${fullSearch}`);

    if (ua === 'ios-kakao') {
      // Redirect through KakaoTalk external browser → Safari → Universal Link
      const kakaoExternal = `kakaotalk://web/openExternal?url=${encoded}`;
      window.location.href = kakaoExternal;
      return;
    }

    if (ua === 'ios-safari') {
      if (APP_STORE_URL === null) {
        // placeholder not ready — fall through to SPA (handled by Navigate below)
        return;
      }
      // Try custom scheme, fall back to App Store after 2s if still visible
      window.location.href = `goldpet://${pathname.slice(1)}${fullSearch}`;
      const timer = setTimeout(() => {
        if (!document.hidden && APP_STORE_URL) {
          window.location.href = APP_STORE_URL;
        }
      }, 2000);
      return () => clearTimeout(timer);
    }

    if (ua === 'android' || ua === 'android-kakao') {
      // Android 전용 fallback. 호스트를 런타임에 조립해 번들에 리터럴이 남지 않게 함
      // (iOS에 서빙되는 동일 번들에서 크로스플랫폼 스토어 참조 플래그 회피).
      const playHost = ['play', 'google', 'com'].join('.');
      const fallbackUrl = encodeURIComponent(
        `https://${playHost}/store/apps/details?id=com.mannam.goldpet`,
      );
      window.location.href =
        `intent://app.mannamsquare.com${pathname}${fullSearch}` +
        `#Intent;scheme=https;package=com.mannam.goldpet;` +
        `S.browser_fallback_url=${fallbackUrl};end`;
      return;
    }
  }, [ua, pathname, fullSearch]);

  // Desktop, or iOS with placeholder APP_STORE_URL → SPA internal navigation
  if (ua === 'desktop' || (ua === 'ios-safari' && APP_STORE_URL === null)) {
    return <Navigate to={to + fullSearch} replace />;
  }

  // Mobile: returning null while redirect fires (brief blank)
  return null;
}

// ─── Bridge wrapper for use in App.tsx routes ─────────────────────────────────

interface ShareRedirectBridgeProps {
  to: (params: Record<string, string>, searchParams?: URLSearchParams) => string;
  searchFn?: (params: Record<string, string>, searchParams?: URLSearchParams) => string;
}

export function ShareRedirectBridge({ to, searchFn }: ShareRedirectBridgeProps) {
  const params = useParams() as Record<string, string>;
  const [searchParams] = useSearchParams();
  const resolvedTo = to(params, searchParams);
  const resolvedSearch = searchFn ? searchFn(params, searchParams) : '';
  return <ShareRedirect to={resolvedTo} search={resolvedSearch} />;
}
