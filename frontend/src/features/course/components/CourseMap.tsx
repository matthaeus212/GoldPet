import { useRef, useEffect, useCallback } from 'react';
import { loadMapsApi } from '../../../hooks/useGoogleMap';
import type { CourseSpotResponse } from '../../../services/courseService';
import { spotLabel } from '../utils/courseHelpers';
import { spotIconUrl } from './SpotIcon';
import { createPolylinePath } from '../../walk/utils/walkMapUtils';

function escapeHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

interface CourseMapProps {
  path: number[][]; // [[lat, lng], ...]
  spots: CourseSpotResponse[];
}

// We need a factory that only works after google.maps is loaded
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let HtmlMarkerOverlayClass: any = null;

function getHtmlMarkerOverlayClass() {
  if (HtmlMarkerOverlayClass) return HtmlMarkerOverlayClass;

  HtmlMarkerOverlayClass = class extends google.maps.OverlayView {
    private position: google.maps.LatLng;
    private html: string;
    private div: HTMLDivElement | null = null;
    private anchorX: number;
    private anchorY: number;

    constructor(map: google.maps.Map, position: google.maps.LatLng, html: string, anchor?: { x: number; y: number }) {
      super();
      this.position = position;
      this.html = html;
      this.anchorX = anchor?.x ?? 0.5;
      this.anchorY = anchor?.y ?? 0.5;
      this.setMap(map);
    }

    onAdd() {
      this.div = document.createElement('div');
      this.div.style.position = 'absolute';
      this.div.innerHTML = this.html;
      const panes = this.getPanes();
      panes?.overlayMouseTarget.appendChild(this.div);
    }

    draw() {
      if (!this.div) return;
      const proj = this.getProjection();
      if (!proj) return;
      const point = proj.fromLatLngToDivPixel(this.position);
      if (!point) return;
      this.div.style.left = `${point.x}px`;
      this.div.style.top = `${point.y}px`;
      this.div.style.transform = `translate(-${this.anchorX * 100}%, -${this.anchorY * 100}%)`;
    }

    onRemove() {
      if (this.div?.parentNode) {
        this.div.parentNode.removeChild(this.div);
        this.div = null;
      }
    }
  };

  return HtmlMarkerOverlayClass;
}

export function CourseMap({ path, spots }: CourseMapProps) {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<google.maps.Map | null>(null);
  const polylineRef = useRef<google.maps.Polyline | null>(null);
  const overlaysRef = useRef<google.maps.OverlayView[]>([]);
  const initializedRef = useRef(false);

  const drawCourse = useCallback(() => {
    const map = mapInstanceRef.current;
    if (!map || initializedRef.current) return;
    if (!path.length) return;

    try {
      // Build LatLng points from [[lat, lng], ...]
      const points = path
        .filter((p) => p.length >= 2 && p[0] != null && p[1] != null)
        .map((p) => ({ lat: p[0], lng: p[1] }));

      if (points.length === 0) return;

      // Draw polyline
      if (polylineRef.current) {
        polylineRef.current.setMap(null);
      }
      polylineRef.current = createPolylinePath(map, points, {
        strokeColor: '#614108',
        strokeWeight: 4,
        strokeOpacity: 0.85,
      });

      // Compute bounds
      const bounds = new google.maps.LatLngBounds();
      for (const pt of points) bounds.extend(pt);

      const Overlay = getHtmlMarkerOverlayClass();

      // Start marker
      overlaysRef.current.push(
        new Overlay(
          map,
          new google.maps.LatLng(points[0].lat, points[0].lng),
          '<div style="width:14px;height:14px;border-radius:50%;background:#614108;border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,0.3);"></div>',
        )
      );

      // End marker
      if (points.length > 1) {
        const last = points[points.length - 1];
        overlaysRef.current.push(
          new Overlay(
            map,
            new google.maps.LatLng(last.lat, last.lng),
            '<div style="width:14px;height:14px;border-radius:50%;background:#E74C3C;border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,0.3);"></div>',
          )
        );
      }

      // Spot markers
      for (const spot of spots) {
        if (spot.latitude == null || spot.longitude == null) continue;
        bounds.extend({ lat: spot.latitude, lng: spot.longitude });
        const iconSrc = spotIconUrl(spot.type);
        const label = escapeHtml(spot.name || spotLabel(spot.type));
        overlaysRef.current.push(
          new Overlay(
            map,
            new google.maps.LatLng(spot.latitude, spot.longitude),
            `<div style="display:flex;flex-direction:column;align-items:center;gap:2px;"><div style="width:32px;height:32px;border-radius:50%;background:#fff;border:1.5px solid #e0d5c8;box-shadow:0 1px 4px rgba(0,0,0,0.18);display:flex;align-items:center;justify-content:center;"><img src="${iconSrc}" width="20" height="20" style="display:block;" alt="" /></div><div style="background:rgba(0,0,0,0.6);color:#fff;font-size:10px;padding:1px 5px;border-radius:4px;white-space:nowrap;max-width:64px;overflow:hidden;text-overflow:ellipsis;">${label}</div></div>`,
          )
        );
      }

      map.fitBounds(bounds, { top: 30, right: 30, bottom: 30, left: 30 });
      initializedRef.current = true;
    } catch (err) {
      console.error('[CourseMap] draw failed:', err);
    }
  }, [path, spots]);

  useEffect(() => {
    let cancelled = false;

    const initMap = () => {
      if (!mapRef.current || mapInstanceRef.current) return;
      const el = mapRef.current;
      if (el.clientWidth === 0 || el.clientHeight === 0) {
        setTimeout(() => { if (!cancelled) initMap(); }, 300);
        return;
      }

      // Determine initial center from path
      const center = path.length > 0
        ? new google.maps.LatLng(path[0][0], path[0][1])
        : new google.maps.LatLng(37.5665, 126.9780);

      try {
        mapInstanceRef.current = new google.maps.Map(el, {
          center,
          zoom: 15,
          mapTypeControl: false,
          zoomControl: false,
          streetViewControl: false,
          fullscreenControl: false,
          scaleControl: false,
          rotateControl: false,
          keyboardShortcuts: false,
          gestureHandling: 'greedy',
        });
        drawCourse();
      } catch (e) {
        console.error('[CourseMap] init failed:', e);
      }
    };

    loadMapsApi()
      .then(() => {
        if (!cancelled) {
          setTimeout(() => { if (!cancelled) initMap(); }, 100);
        }
      })
      .catch((e) => {
        console.error('[CourseMap] Failed to load Google Maps API:', e);
      });

    return () => {
      cancelled = true;
      for (const o of overlaysRef.current) o.setMap(null);
      overlaysRef.current = [];
      if (polylineRef.current) {
        polylineRef.current.setMap(null);
        polylineRef.current = null;
      }
      // Google Maps has no destroy() — null the reference for GC
      mapInstanceRef.current = null;
      initializedRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Re-draw when path/spots change after map is already initialized
  useEffect(() => {
    if (!mapInstanceRef.current || initializedRef.current) return;
    drawCourse();
  }, [drawCourse]);

  return (
    <div
      ref={mapRef}
      style={{ width: '100%', height: '100%' }}
      aria-label="코스 지도"
    />
  );
}

export default CourseMap;
