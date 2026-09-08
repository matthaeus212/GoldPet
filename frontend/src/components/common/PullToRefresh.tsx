import { useEffect, useRef, useState, useCallback } from 'react';
import { useLocation } from 'react-router-dom';
import { queryClient } from '../../lib/queryClient';

const THRESHOLD = 60;
const MAX_PULL = 120;
const ACTIVATION_DY = 30;
const MIN_SPINNER_MS = 400;

const PTR_ENABLED_ROUTES = [
    '/home',
    '/community',
    '/walk',
    '/chat',
    '/friend-list',
    '/friend-find',
    '/notifications',
    '/places',
    '/mypage',
    '/my-walks',
    '/gold',
    '/courses',
];

export const PullToRefresh = () => {
    const { pathname } = useLocation();
    const isEnabled = PTR_ENABLED_ROUTES.includes(pathname);

    const startY = useRef(0);
    const startX = useRef(0);
    const activeRef = useRef(false);
    const directionLocked = useRef(false);
    const nonPassiveAdded = useRef(false);
    const [pullDistance, setPullDistance] = useState(0);
    const [releasing, setReleasing] = useState(false);

    // Non-passive touchmove handler for preventing scroll during PTR
    const nonPassiveHandler = useRef<((e: TouchEvent) => void) | null>(null);

    const cleanupNonPassive = useCallback(() => {
        if (nonPassiveAdded.current && nonPassiveHandler.current) {
            document.removeEventListener('touchmove', nonPassiveHandler.current);
            nonPassiveAdded.current = false;
        }
    }, []);

    const onTouchStart = useCallback((e: TouchEvent) => {
        if (releasing) return;

        // Check if #root (the real scroll container) is scrolled
        const root = document.getElementById('root');
        if (root && root.scrollTop > 0) return;

        // Check if touch started inside a scrollable container that isn't at top
        let el = e.target as HTMLElement | null;
        while (el && el !== document.body) {
            if (el.scrollHeight > el.clientHeight && el.scrollTop > 0) {
                const style = window.getComputedStyle(el);
                const overflowY = style.overflowY;
                if (overflowY === 'auto' || overflowY === 'scroll') {
                    return;
                }
            }
            el = el.parentElement;
        }

        startY.current = e.touches[0].clientY;
        startX.current = e.touches[0].clientX;
        activeRef.current = true;
        directionLocked.current = false;
    }, [releasing]);

    const onTouchMove = useCallback((e: TouchEvent) => {
        if (!activeRef.current) return;

        const dy = e.touches[0].clientY - startY.current;
        const dx = e.touches[0].clientX - startX.current;

        // Before direction is locked, check movement intent
        if (!directionLocked.current) {
            // Need at least ACTIVATION_DY to evaluate direction
            if (Math.abs(dy) < ACTIVATION_DY && Math.abs(dx) < ACTIVATION_DY) return;

            // Horizontal movement dominant → abort PTR
            if (Math.abs(dx) > Math.abs(dy)) {
                activeRef.current = false;
                setPullDistance(0);
                return;
            }

            // Upward movement → abort PTR
            if (dy <= 0) {
                activeRef.current = false;
                setPullDistance(0);
                return;
            }

            // Vertical downward confirmed — lock direction
            directionLocked.current = true;

            // Add non-passive listener to enable preventDefault
            nonPassiveHandler.current = (ev: TouchEvent) => {
                if (directionLocked.current && activeRef.current) {
                    ev.preventDefault();
                }
            };
            document.addEventListener('touchmove', nonPassiveHandler.current, { passive: false });
            nonPassiveAdded.current = true;
        }

        // Direction is locked vertical — update pull distance
        const root = document.getElementById('root');
        if (dy > 0 && (!root || root.scrollTop <= 0)) {
            setPullDistance(Math.min(dy * 0.4, MAX_PULL));
        } else {
            activeRef.current = false;
            directionLocked.current = false;
            setPullDistance(0);
            cleanupNonPassive();
        }
    }, [cleanupNonPassive]);

    const onTouchEnd = useCallback(() => {
        if (!activeRef.current) {
            cleanupNonPassive();
            return;
        }
        activeRef.current = false;
        directionLocked.current = false;
        cleanupNonPassive();

        if (pullDistance >= THRESHOLD) {
            setReleasing(true);
            setPullDistance(THRESHOLD);

            const refreshStart = Date.now();

            queryClient.invalidateQueries().then(() => {
                const elapsed = Date.now() - refreshStart;
                const remaining = Math.max(MIN_SPINNER_MS - elapsed, 0);
                setTimeout(() => {
                    document.getElementById('root')?.scrollTo({ top: 0, behavior: 'smooth' });
                    setReleasing(false);
                    setPullDistance(0);
                }, remaining);
            }).catch(() => {
                // On failure: dismiss spinner, keep existing data
                setReleasing(false);
                setPullDistance(0);
            });
        } else {
            setPullDistance(0);
        }
    }, [pullDistance, cleanupNonPassive]);

    useEffect(() => {
        if (!isEnabled) return;

        document.addEventListener('touchstart', onTouchStart, { passive: true });
        document.addEventListener('touchmove', onTouchMove, { passive: true });
        document.addEventListener('touchend', onTouchEnd);
        return () => {
            document.removeEventListener('touchstart', onTouchStart);
            document.removeEventListener('touchmove', onTouchMove);
            document.removeEventListener('touchend', onTouchEnd);
            cleanupNonPassive();
        };
    }, [isEnabled, onTouchStart, onTouchMove, onTouchEnd, cleanupNonPassive]);

    if (!isEnabled || pullDistance <= 0) return null;

    const progress = Math.min(pullDistance / THRESHOLD, 1);
    const pastThreshold = pullDistance >= THRESHOLD;

    return (
        <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            zIndex: 99999,
            display: 'flex',
            justifyContent: 'center',
            paddingTop: Math.max(pullDistance - 20, 0),
            pointerEvents: 'none',
        }}>
            <div style={{
                width: 40,
                height: 40,
                borderRadius: '50%',
                backgroundColor: pastThreshold ? 'var(--color-primary)' : '#fff',
                boxShadow: '0 2px 12px rgba(0,0,0,0.25)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transform: releasing ? 'scale(1.1)' : `scale(${0.6 + progress * 0.4})`,
                transition: releasing ? 'transform 0.2s' : 'none',
                animation: releasing ? 'ptr-spin 0.6s linear infinite' : 'none',
            }}>
                <svg
                    width="22" height="22" viewBox="0 0 24 24" fill="none"
                    stroke={pastThreshold ? '#fff' : 'var(--color-primary)'}
                    strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"
                    style={{
                        transform: `rotate(${progress * 360}deg)`,
                        transition: releasing ? 'none' : 'transform 0.05s',
                    }}
                >
                    <path d="M21 12a9 9 0 1 1-6.22-8.56" />
                    <path d="M21 3v6h-6" />
                </svg>
            </div>
            <style>{`
                @keyframes ptr-spin {
                    from { transform: rotate(0deg) scale(1.1); }
                    to { transform: rotate(360deg) scale(1.1); }
                }
            `}</style>
        </div>
    );
};
