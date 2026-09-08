import { logger } from '../utils/logger';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

interface UseRouterRecoveryGuardOptions {
  /**
   * Path to redirect to when a direct-entry + stale-history situation is detected.
   * Defaults to '/community'.
   */
  fallbackPath?: string;
}

/**
 * §S3 Router Recovery Guard
 *
 * Detects WebView history mismatches that arise when a user deep-links
 * directly to /users/:userId (e.g. from a notification or external link)
 * and then presses hardware/swipe-back, which would leave them on a blank
 * or broken page because there is no previous history entry.
 *
 * Recovery strategy:
 *   1. On mount, check whether this is a "direct entry" (history.length === 1).
 *   2. If so, rewrite the history stack: replace current state with fallbackPath
 *      so the back button always has somewhere safe to land.
 *
 * This does NOT redirect the user away — it only patches the history stack
 * so that a subsequent back-navigation lands on `fallbackPath` instead of
 * an empty/broken state.
 *
 * A `router.recovery.triggered` event is emitted via console and a custom
 * DOM event for future observability wiring (Sentry, analytics, etc.).
 */
export function useRouterRecoveryGuard({
  fallbackPath = '/community',
}: UseRouterRecoveryGuardOptions = {}) {
  const navigate = useNavigate();

  useEffect(() => {
    // Direct entry: no previous page in the WebView history stack.
    // history.length === 1 means there is no page to go back to.
    if (history.length <= 1) {
      logger.debug('[router.recovery.triggered] direct entry detected — patching history stack', {
        currentPath: window.location.pathname,
        fallbackPath,
        historyLength: history.length,
      });

      // Emit observable event for analytics / Sentry (fire-and-forget)
      window.dispatchEvent(
        new CustomEvent('gp-router-recovery', {
          detail: {
            trigger: 'direct-entry',
            from: window.location.pathname,
            to: fallbackPath,
          },
        }),
      );

      // Inject a "safe" back-destination into the history stack so that
      // back-navigation from /users/:userId lands on fallbackPath.
      // We use navigate() with replace=false so the user can still go back
      // to the profile page after landing on the community list.
      navigate(fallbackPath, { replace: false });
      // Then navigate back to the current profile page so the user stays here.
      navigate(1);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
}
