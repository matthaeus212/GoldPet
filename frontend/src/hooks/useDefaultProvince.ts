import { useState, useEffect } from 'react';
import { nativeBridge } from '../bridge/nativeBridge';
import { locationService } from '../services/locationService';

const CACHE_KEY = 'goldpet_default_province';

export function useDefaultProvince(): string | undefined {
  const [province, setProvince] = useState<string | undefined>(() => {
    const cached = sessionStorage.getItem(CACHE_KEY);
    // null = not cached yet; '' = cached but no province (all regions)
    return cached !== null ? (cached || undefined) : undefined;
  });

  useEffect(() => {
    if (sessionStorage.getItem(CACHE_KEY) !== null) return;

    let cancelled = false;

    async function detect() {
      try {
        if (!nativeBridge.isAvailable()) return;
        const loc = await nativeBridge.callMethod('getCurrentLocation') as { latitude: number; longitude: number };
        if (cancelled) return;
        const result = await locationService.reverseGeocode(loc.latitude, loc.longitude);
        if (cancelled) return;
        const detected = result.province ?? undefined;
        sessionStorage.setItem(CACHE_KEY, detected ?? '');
        setProvince(detected);
      } catch {
        // GPS failure or API error → fall back to undefined (전체)
      }
    }

    detect();
    return () => { cancelled = true; };
  }, []);

  return province;
}
