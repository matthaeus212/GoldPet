import { useCallback } from 'react';

/**
 * iOS WKWebView 키보드 dismiss 훅.
 * 컨테이너 div에 onTouchStart + onTouchEnd를 연결하면
 * 입력 필드 외부 터치 시 키보드가 닫히고,
 * 입력 필드 터치 시 정상적으로 포커스됩니다.
 *
 * Usage:
 *   const keyboardDismiss = useKeyboardDismiss();
 *   <div {...keyboardDismiss}>...</div>
 */
export function useKeyboardDismiss() {
  const onTouchStart = useCallback((e: React.TouchEvent) => {
    const active = document.activeElement;
    if (active instanceof HTMLElement && active !== e.target) {
      const target = e.target as HTMLElement;
      // 버튼 등 인터랙티브 요소 터치 시 blur 하지 않음 (click 이벤트 보호)
      if (target.closest('button, a, [role="button"]')) return;
      active.blur();
    }
  }, []);

  const onTouchEnd = useCallback((e: React.TouchEvent) => {
    const tag = (e.target as HTMLElement).tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
      (e.target as HTMLElement).focus();
    }
  }, []);

  return { onTouchStart, onTouchEnd };
}
