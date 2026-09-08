import { useRef, useCallback, useEffect } from 'react';

interface LongPressHandlers {
    onTouchStart: (e: React.TouchEvent) => void;
    onTouchEnd: () => void;
    onTouchMove: (e: React.TouchEvent) => void;
    onContextMenu: (e: React.MouseEvent) => void;
}

const MOVE_THRESHOLD = 10; // px - prevents false cancellation on minor finger drift

export function useLongPress(
    onLongPress: (position: { x: number; y: number }) => void,
    delay = 500
): LongPressHandlers {
    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const startPosRef = useRef<{ x: number; y: number } | null>(null);

    const clear = useCallback(() => {
        if (timerRef.current) {
            clearTimeout(timerRef.current);
            timerRef.current = null;
        }
        startPosRef.current = null;
    }, []);

    // Cleanup on unmount
    useEffect(() => clear, [clear]);

    const onTouchStart = useCallback((e: React.TouchEvent) => {
        const touch = e.touches[0];
        startPosRef.current = { x: touch.clientX, y: touch.clientY };
        timerRef.current = setTimeout(() => {
            onLongPress({ x: touch.clientX, y: touch.clientY });
            timerRef.current = null;
        }, delay);
    }, [onLongPress, delay]);

    const onTouchMove = useCallback((e: React.TouchEvent) => {
        if (!startPosRef.current) return;
        const touch = e.touches[0];
        const dx = Math.abs(touch.clientX - startPosRef.current.x);
        const dy = Math.abs(touch.clientY - startPosRef.current.y);
        if (dx > MOVE_THRESHOLD || dy > MOVE_THRESHOLD) {
            clear();
        }
    }, [clear]);

    const onTouchEnd = useCallback(() => { clear(); }, [clear]);

    const onContextMenu = useCallback((e: React.MouseEvent) => {
        e.preventDefault();
        onLongPress({ x: e.clientX, y: e.clientY });
    }, [onLongPress]);

    return { onTouchStart, onTouchEnd, onTouchMove, onContextMenu };
}
