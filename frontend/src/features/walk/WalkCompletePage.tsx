import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { loadMapsApi } from '../../hooks/useGoogleMap';
import { walkService } from '../../services/walkService';
import type { WalkSpot } from '../../services/walkService';
import { spotMarkerImage, petCircleMarkerHtml, createPolylinePath } from './utils/walkMapUtils';
import './WalkCompletePage.css';
import './styles/walkMapMarkers.css';

export default function WalkCompletePage() {
  const { walkId } = useParams<{ walkId: string }>();
  const navigate = useNavigate();

  const { data: walk } = useQuery({
    queryKey: ['walk', 'complete', walkId],
    queryFn: () => walkService.getWalkDetail(Number(walkId)),
    enabled: !!walkId,
  });

  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<google.maps.Map | null>(null);
  const polylineRef = useRef<google.maps.Polyline | null>(null);
  const spotMarkersRef = useRef<google.maps.Marker[]>([]);
  const petOverlaysRef = useRef<Array<{ setMap: (m: google.maps.Map | null) => void }>>([]);
  const [mapReady, setMapReady] = useState(false);
  const [mapLoadFailed, setMapLoadFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const initMap = (lat: number, lng: number) => {
      if (!mapRef.current || mapInstanceRef.current) return;
      const el = mapRef.current;
      if (el.clientWidth === 0 || el.clientHeight === 0) {
        setTimeout(() => { if (!cancelled) initMap(lat, lng); }, 200);
        return;
      }
      try {
        mapInstanceRef.current = new google.maps.Map(el, {
          center: new google.maps.LatLng(lat, lng),
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
        setMapReady(true);
      } catch {
        setMapLoadFailed(true);
      }
    };

    loadMapsApi()
      .then(() => {
        if (cancelled) return;
        const lat = walk?.startLatitude ?? 37.5665;
        const lng = walk?.startLongitude ?? 126.9780;
        setTimeout(() => { if (!cancelled) initMap(lat, lng); }, 100);
      })
      .catch(() => { if (!cancelled) setMapLoadFailed(true); });

    return () => {
      cancelled = true;
      spotMarkersRef.current.forEach(m => m.setMap(null));
      spotMarkersRef.current = [];
      petOverlaysRef.current.forEach(o => o.setMap(null));
      petOverlaysRef.current = [];
      if (polylineRef.current) {
        polylineRef.current.setMap(null);
        polylineRef.current = null;
      }
      mapInstanceRef.current = null;
      setMapReady(false);
    };
  }, [walk?.startLatitude, walk?.startLongitude]);

  useEffect(() => {
    if (!mapReady || !mapInstanceRef.current || !walk?.pathPoints?.length) return;
    try {
      if (polylineRef.current) polylineRef.current.setMap(null);
      spotMarkersRef.current.forEach(m => m.setMap(null));
      spotMarkersRef.current = [];
      petOverlaysRef.current.forEach(o => o.setMap(null));
      petOverlaysRef.current = [];

      const points = walk.pathPoints
        .filter(p => p.lat != null && p.lng != null)
        .map(p => ({ lat: p.lat, lng: p.lng }));
      if (points.length === 0) return;

      polylineRef.current = createPolylinePath(mapInstanceRef.current, points, {
        strokeColor: '#614108',
        strokeWeight: 4,
        strokeOpacity: 0.9,
      });

      const bounds = new google.maps.LatLngBounds();
      points.forEach(p => bounds.extend(new google.maps.LatLng(p.lat, p.lng)));

      const petImg = walk.petProfileImageUrls?.[0] ?? undefined;
      const PetOverlay = class extends google.maps.OverlayView {
        private pos: google.maps.LatLng;
        private html: string;
        private div: HTMLDivElement | null = null;
        constructor(pos: google.maps.LatLng, html: string) {
          super();
          this.pos = pos;
          this.html = html;
        }
        onAdd() {
          this.div = document.createElement('div');
          this.div.style.position = 'absolute';
          this.div.style.pointerEvents = 'none';
          this.div.innerHTML = this.html;
          this.getPanes()!.overlayLayer.appendChild(this.div);
        }
        draw() {
          if (!this.div) return;
          const px = this.getProjection().fromLatLngToDivPixel(this.pos);
          if (px) {
            this.div.style.left = (px.x - 26) + 'px';
            this.div.style.top = (px.y - 26) + 'px';
          }
        }
        onRemove() {
          if (this.div?.parentNode) this.div.parentNode.removeChild(this.div);
          this.div = null;
        }
      };

      const startOverlay = new PetOverlay(
        new google.maps.LatLng(points[0].lat, points[0].lng),
        petCircleMarkerHtml('start', petImg),
      );
      startOverlay.setMap(mapInstanceRef.current);
      const endOverlay = new PetOverlay(
        new google.maps.LatLng(points[points.length - 1].lat, points[points.length - 1].lng),
        petCircleMarkerHtml('end', petImg),
      );
      endOverlay.setMap(mapInstanceRef.current);
      petOverlaysRef.current.push(
        { setMap: (m) => startOverlay.setMap(m) },
        { setMap: (m) => endOverlay.setMap(m) },
      );

      if (walk.spots?.length) {
        walk.spots.forEach((spot: WalkSpot) => {
          if (spot.latitude == null || spot.longitude == null) return;
          const pos = new google.maps.LatLng(spot.latitude, spot.longitude);
          bounds.extend(pos);
          const marker = new google.maps.Marker({
            map: mapInstanceRef.current!,
            position: pos,
            icon: {
              url: spotMarkerImage(spot.type ?? 'OTHER'),
              scaledSize: new google.maps.Size(32, 32),
              anchor: new google.maps.Point(16, 16),
            },
          });
          spotMarkersRef.current.push(marker);
        });
      }

      if (points.length > 1) {
        mapInstanceRef.current.fitBounds(bounds, { top: 40, right: 40, bottom: 40, left: 40 });
      }
    } catch (err) {
      console.error('[WalkComplete] route drawing failed', err);
    }
  }, [mapReady, walk]);

  const distanceKm = walk?.distance ?? 0;
  const calories = walk?.calories ?? 0;
  const durationMin = walk?.durationSeconds ? Math.round(walk.durationSeconds / 60) : 0;
  const gold = Math.round(distanceKm * 10);
  const address = walk?.startAddress ?? '';

  const goBack = () => navigate('/walk', { replace: true });
  const goDetail = () => navigate(`/walk/detail/${walkId}`, { replace: true });

  return (
    <div className="walk-complete-page">
      <header className="walk-complete-header">
        <button type="button" className="walk-complete-back" onClick={goBack} aria-label="뒤로가기">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M15 6l-6 6 6 6" stroke="#614108" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      </header>

      <div className="walk-complete-body">
        <div className="walk-complete-map-wrap">
          {mapLoadFailed ? (
            <div className="walk-complete-map-fail">지도를 불러올 수 없습니다</div>
          ) : (
            <div ref={mapRef} className="walk-complete-map" />
          )}
          {address && (
            <div className="walk-complete-location">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path d="M8 1.333c-2.577 0-4.667 2.09-4.667 4.667 0 3.5 4.667 8.667 4.667 8.667s4.667-5.167 4.667-8.667c0-2.577-2.09-4.667-4.667-4.667Zm0 6.334A1.667 1.667 0 1 1 8 4.333a1.667 1.667 0 0 1 0 3.334Z" fill="#614108" />
              </svg>
              <span>{address}</span>
            </div>
          )}
        </div>

        <section className="walk-complete-summary">
          <h2 className="walk-complete-title">산책 하느라 고생하셨어요!</h2>

          <div className="walk-complete-stats">
            <div className="walk-complete-row">
              <div className="walk-complete-stat">
                <p className="walk-complete-stat-label">거리(km)</p>
                <p className="walk-complete-stat-value">{distanceKm.toFixed(1)}</p>
              </div>
              <div className="walk-complete-stat">
                <p className="walk-complete-stat-label">시간(분)</p>
                <p className="walk-complete-stat-value">{durationMin.toLocaleString()}</p>
              </div>
            </div>
            <div className="walk-complete-row">
              <div className="walk-complete-stat">
                <p className="walk-complete-stat-label">칼로리(Kcal)</p>
                <p className="walk-complete-stat-value">{calories.toLocaleString()}</p>
              </div>
              <div className="walk-complete-stat">
                <p className="walk-complete-stat-label">적립(Gold)</p>
                <p className="walk-complete-stat-value">{gold.toLocaleString()}</p>
              </div>
            </div>
          </div>

          <button type="button" className="walk-complete-detail-btn" onClick={goDetail}>
            <span>우리의 산책 기록 바로가기</span>
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M6 4l4 4-4 4" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        </section>
      </div>
    </div>
  );
}
