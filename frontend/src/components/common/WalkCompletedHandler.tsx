import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { nativeBridge } from '../../bridge/nativeBridge';

/**
 * Listens for the `walkCompleted` native event dispatched by Flutter after a
 * successful walk save, and navigates the WebView to the walk completion brief
 * page (`/walk/complete/:id`).
 */
export function WalkCompletedHandler() {
  const navigate = useNavigate();

  useEffect(() => {
    const unsub = nativeBridge.onEvent('walkCompleted', (raw) => {
      const data = raw as { walkId?: number | string } | undefined;
      const walkId = Number(data?.walkId);
      if (!Number.isFinite(walkId)) return;
      navigate(`/walk/complete/${walkId}`, { replace: true });
    });
    return () => unsub();
  }, [navigate]);

  return null;
}
