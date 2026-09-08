import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { WalkPhotoGalleryViewer } from './WalkPhotoGalleryViewer';
import { WalkPhotoErrorBoundary } from './WalkPhotoErrorBoundary';
import type { WalkPhotoItem } from '../../../services/walkService';
import { useOverlayColor } from '../../../hooks/useOverlayColor';

type Props = {
  mode: 'owned' | 'shared';
  walkId: number;
  initialSpotId: number;
  initialPhoto?: WalkPhotoItem | null;
  onClose: () => void;
};

function buildPath(mode: 'owned' | 'shared', walkId: number, spotId: number): string {
  return mode === 'owned'
    ? `/walk-photos/${walkId}/${spotId}`
    : `/walk-shared-photos/${walkId}/${spotId}`;
}

export function WalkPhotoGalleryModal({
  mode,
  walkId,
  initialSpotId,
  initialPhoto,
  onClose,
}: Props) {
  useOverlayColor(true, '#000000', '#000000');

  const rootRef = useRef<HTMLDivElement>(null);
  const previousActiveRef = useRef<HTMLElement | null>(null);
  const closedByPopstateRef = useRef(false);
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  // Sentinel history + popstate + ESC + body scroll lock + focus
  useEffect(() => {
    const origPath = window.location.pathname + window.location.search;
    const initialPath = buildPath(mode, walkId, initialSpotId);
    window.history.pushState({ wpvSentinel: true, origPath }, '', initialPath);

    const onPop = () => {
      closedByPopstateRef.current = true;
      onCloseRef.current();
    };
    window.addEventListener('popstate', onPop);

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current();
    };
    window.addEventListener('keydown', onKey);

    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    previousActiveRef.current = (document.activeElement as HTMLElement | null) ?? null;
    rootRef.current?.focus();

    return () => {
      window.removeEventListener('popstate', onPop);
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
      try {
        previousActiveRef.current?.focus?.();
      } catch {
        // ignore
      }
      if (!closedByPopstateRef.current && window.history.state?.wpvSentinel) {
        window.history.back();
      }
    };
  }, [mode, walkId, initialSpotId]);

  const handleRequestPath = (path: string) => {
    const state = window.history.state;
    const origPath = state?.origPath ?? window.location.pathname + window.location.search;
    window.history.replaceState({ wpvSentinel: true, origPath }, '', path);
  };

  const onBackdropClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) onClose();
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'Tab') return;
    const root = rootRef.current;
    if (!root) return;
    const focusables = root.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    );
    if (focusables.length === 0) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    const active = document.activeElement as HTMLElement | null;
    if (e.shiftKey && (active === first || active === root)) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && active === last) {
      e.preventDefault();
      first.focus();
    }
  };

  return createPortal(
    <div className="wpv_backdrop" onClick={onBackdropClick} role="presentation">
      <div
        ref={rootRef}
        className="wpv_modal"
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        onKeyDown={onKeyDown}
      >
        <WalkPhotoErrorBoundary
          fallbackHref={mode === 'owned' ? '/walk-photos' : '/walk-shared-photos'}
        >
          <WalkPhotoGalleryViewer
            // 다른 사진 클릭 시 Viewer 상태(hasHydratedRef, activeIndex 등) 완전 초기화
            key={`${mode}-${walkId}-${initialSpotId}`}
            variant="modal"
            mode={mode}
            walkId={walkId}
            initialSpotId={initialSpotId}
            initialPhoto={initialPhoto ?? null}
            onClose={onClose}
            onRequestPath={handleRequestPath}
          />
        </WalkPhotoErrorBoundary>
      </div>
    </div>,
    document.body,
  );
}

export default WalkPhotoGalleryModal;
