import { useEffect } from 'react';

/**
 * Sets --app-height CSS custom property on document.documentElement.
 * Bridges the gap between CSS 100vh (which doesn't update in iOS WKWebView
 * when Flutter resizes the WebView for keyboard) and the actual visible height.
 *
 * Call once in App.tsx — the CSS variable is globally available.
 */
export function useAppHeight() {
    useEffect(() => {
        let resizeTimer: ReturnType<typeof setTimeout> | null = null;

        const getVisibleHeight = () => {
            const viewport = window.visualViewport;
            if (viewport) {
                return Math.min(viewport.height, window.innerHeight);
            }
            return window.innerHeight;
        };

        const updateAppHeight = () => {
            const h = getVisibleHeight();
            document.documentElement.style.setProperty('--app-height', `${h}px`);
        };

        // Set initial value
        updateAppHeight();

        const viewport = window.visualViewport;

        const handleResize = () => {
            // Update immediately
            updateAppHeight();

            // Re-measure after iOS keyboard animation settles (~300ms)
            if (resizeTimer) clearTimeout(resizeTimer);
            resizeTimer = setTimeout(updateAppHeight, 300);
        };

        // visualViewport.resize: Android WebView
        // window.resize: iOS WKWebView (Flutter resizes the WebView when keyboard opens)
        if (viewport) {
            viewport.addEventListener('resize', handleResize);
        }
        window.addEventListener('resize', handleResize);

        return () => {
            if (resizeTimer) clearTimeout(resizeTimer);
            if (viewport) {
                viewport.removeEventListener('resize', handleResize);
            }
            window.removeEventListener('resize', handleResize);
        };
    }, []);
}
