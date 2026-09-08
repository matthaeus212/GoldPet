import { useEffect } from 'react';
import { nativeBridge } from '../bridge/nativeBridge';

let overlayRefCount = 0;
let activeParams: { color: string; bottomColor?: string } | null = null;

// Flutter hybrid_webview._updateSystemBarColors는 SPA URL 변경 시 `_overlayColor`를
// safety net으로 null 리셋함. pushState/replaceState 이후 overlay를 재적용하기 위해
// history 메서드를 1회 패치하여 custom event를 발행.
let historyPatched = false;
function ensureHistoryPatched() {
  if (historyPatched) return;
  historyPatched = true;
  const origPush = window.history.pushState.bind(window.history);
  const origReplace = window.history.replaceState.bind(window.history);
  window.history.pushState = function (data, unused, url) {
    origPush(data, unused, url);
    window.dispatchEvent(new Event('omc-history-changed'));
  };
  window.history.replaceState = function (data, unused, url) {
    origReplace(data, unused, url);
    window.dispatchEvent(new Event('omc-history-changed'));
  };
}

function applyOverlay(params: { color: string; bottomColor?: string }) {
  const payload: Record<string, string> = { color: params.color };
  if (params.bottomColor) payload.bottomColor = params.bottomColor;
  nativeBridge.callMethod('setOverlayColor', payload).catch(() => {});
}

/**
 * 오버레이 모달/페이지가 활성 상태일 때 Flutter SafeArea 색상을 동기화하는 훅.
 * 레퍼런스 카운팅으로 스택 모달을 안전하게 처리하고, SPA URL 변경 후
 * Flutter의 safety-net 리셋에 대비해 overlay를 재적용합니다.
 *
 * @param isOpen - 오버레이가 열려있는지 여부
 * @param color - 상단 SafeArea 오버레이 색상 (기본값: '#000000')
 * @param bottomColor - 하단 SafeArea 색상 (미지정 시 color와 동일)
 */
export function useOverlayColor(isOpen: boolean, color = '#000000', bottomColor?: string): void {
  useEffect(() => {
    if (!isOpen) return;
    if (!nativeBridge.isAvailable()) return;

    ensureHistoryPatched();

    overlayRefCount++;
    activeParams = { color, bottomColor };
    if (overlayRefCount === 1) applyOverlay(activeParams);

    const reapply = () => {
      if (overlayRefCount > 0 && activeParams) applyOverlay(activeParams);
    };
    window.addEventListener('popstate', reapply);
    window.addEventListener('omc-history-changed', reapply);

    return () => {
      window.removeEventListener('popstate', reapply);
      window.removeEventListener('omc-history-changed', reapply);
      overlayRefCount--;
      if (overlayRefCount === 0) {
        activeParams = null;
        nativeBridge.callMethod('clearOverlayColor').catch(() => {});
      }
    };
  }, [isOpen, color, bottomColor]);
}
