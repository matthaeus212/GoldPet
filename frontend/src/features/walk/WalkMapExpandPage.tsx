import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { loadMapsApi } from '../../hooks/useGoogleMap';
import { walkService } from '../../services/walkService';
import type { WalkSpot } from '../../services/walkService';
import { MainHeader } from '../../components/common/MainHeader';
import { BottomNav } from '../../components/common/BottomNav';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';
import { spotMarkerImage, spotTypeLabel, spotTypeMarkerIcon, formatSpotTimestamp, petCircleMarkerHtml, createPolylinePath } from './utils/walkMapUtils';
import { useWalkPhotoGallery } from './hooks/useWalkPhotoGallery';
import { useShare } from '../share';
import './WalkMapExpand.css';
import './styles/walkMapMarkers.css';

const DEFAULT_LAT = 37.5665;
const DEFAULT_LNG = 126.9780;

export const WalkMapExpandPage = () => {
    const navigate = useNavigate();
    const { walkId } = useParams<{ walkId: string }>();
    const mapRef = useRef<HTMLDivElement>(null);
    const mapInstanceRef = useRef<google.maps.Map | null>(null);
    const polylineRef = useRef<google.maps.Polyline | null>(null);
    const spotMarkersRef = useRef<google.maps.Marker[]>([]);
    const petMarkersRef = useRef<google.maps.Marker[]>([]);
    const listenersRef = useRef<google.maps.MapsEventListener[]>([]);
    const [address, setAddress] = useState('위치 확인 중...');
    const [mapLoadFailed, setMapLoadFailed] = useState(false);
    const [mapReady, setMapReady] = useState(false);
    const [selectedSpot, setSelectedSpot] = useState<WalkSpot | null>(null);
    const { share } = useShare();

    // Load walk data (shares cache with WalkDetailPage)
    const { data: walk } = useQuery({
        queryKey: ['walk', 'detail', walkId],
        queryFn: () => walkService.getWalkDetail(Number(walkId)),
        enabled: !!walkId,
    });

    const {
        resolvedUrls: galleryUrls, galleryOpen, initialIndex,
        previewUrl, previewLoading, previewError,
        openGallery, closeGallery, resolvePreview, clearPreview,
    } = useWalkPhotoGallery(walk?.spots);

    // Reverse geocode from start coords
    useEffect(() => {
        if (!walk?.startLatitude || !walk?.startLongitude) return;
        import('../../services/api/client').then(({ default: apiClient }) => {
            apiClient.get(`/maps/reverse-geocode?lat=${walk.startLatitude}&lng=${walk.startLongitude}`)
                .then(res => setAddress(res.data.address))
                .catch(() => {});
        }).catch(() => {});
    }, [walk]);

    // Init map
    useEffect(() => {
        let cancelled = false;
        let resizeObserver: ResizeObserver | null = null;

        const initMap = (lat: number, lng: number) => {
            if (!mapRef.current || mapInstanceRef.current) return;
            const el = mapRef.current;
            if (el.clientWidth === 0 || el.clientHeight === 0) {
                setTimeout(() => { if (!cancelled) initMap(lat, lng); }, 300);
                return;
            }
            try {
                const center = new google.maps.LatLng(lat, lng);
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
                setMapReady(true);
                // Track container resizes and trigger Google Maps resize
                const syncMapSize = () => {
                    if (mapInstanceRef.current && mapRef.current) {
                        const w = mapRef.current.clientWidth;
                        const h = mapRef.current.clientHeight;
                        if (w > 0 && h > 0) {
                            google.maps.event.trigger(mapInstanceRef.current, 'resize');
                        }
                    }
                };
                // Sync after flex layout settles
                requestAnimationFrame(() => {
                    setTimeout(() => {
                        if (!cancelled) syncMapSize();
                    }, 200);
                });
                // Keep synced on future resizes (orientation change, etc.)
                resizeObserver = new ResizeObserver(() => syncMapSize());
                resizeObserver.observe(el);
            } catch {
                setMapLoadFailed(true);
            }
        };

        const setup = () => {
            if (cancelled) return;
            const lat = walk?.startLatitude ?? DEFAULT_LAT;
            const lng = walk?.startLongitude ?? DEFAULT_LNG;
            setTimeout(() => { if (!cancelled) initMap(lat, lng); }, 100);
        };

        loadMapsApi()
            .then(() => {
                if (!cancelled) setup();
            })
            .catch((e) => {
                console.error('[WalkMapExpand] Failed to load Google Maps API:', e);
                if (!cancelled) setMapLoadFailed(true);
            });

        return () => {
            cancelled = true;
            if (resizeObserver) resizeObserver.disconnect();
            // Clean up all markers and listeners
            listenersRef.current.forEach(l => {
                try { google.maps.event.removeListener(l); } catch (e) { console.error('WalkMapExpand: failed to remove listener', e); }
            });
            listenersRef.current = [];
            spotMarkersRef.current.forEach(m => m.setMap(null));
            spotMarkersRef.current = [];
            petMarkersRef.current.forEach(m => m.setMap(null));
            petMarkersRef.current = [];
            if (polylineRef.current) {
                polylineRef.current.setMap(null);
                polylineRef.current = null;
            }
            // Google Maps has no destroy() — null the reference for GC
            mapInstanceRef.current = null;
            // Reset so route drawing effect re-fires when new map is created
            setMapReady(false);
        };
    }, [walk?.startLatitude, walk?.startLongitude]);

    // Draw route polyline once map is ready and walk data loaded
    useEffect(() => {
        if (!mapReady || !mapInstanceRef.current || !walk?.pathPoints?.length) return;
        try {
            if (polylineRef.current) {
                polylineRef.current.setMap(null);
            }
            // Clean previous markers
            listenersRef.current.forEach(l => {
                try { google.maps.event.removeListener(l); } catch (e) { console.error('WalkMapExpand: failed to remove listener', e); }
            });
            listenersRef.current = [];
            spotMarkersRef.current.forEach(m => m.setMap(null));
            spotMarkersRef.current = [];
            petMarkersRef.current.forEach(m => m.setMap(null));
            petMarkersRef.current = [];

            const path = walk.pathPoints
                .filter(p => p.lat != null && p.lng != null)
                .map(p => ({ lat: p.lat, lng: p.lng }));
            if (path.length === 0) return;

            // Use Google Maps Polyline
            polylineRef.current = createPolylinePath(mapInstanceRef.current, path, {
                strokeColor: '#6B9A2B',
                strokeWeight: 4,
                strokeOpacity: 0.9,
            });

            // Build bounds from path + spots
            const bounds = new google.maps.LatLngBounds();
            path.forEach(p => bounds.extend(new google.maps.LatLng(p.lat, p.lng)));

            // Pet markers at start and end
            if (path.length > 0) {
                const petImg = walk.petProfileImageUrls?.[0];

                const PetOverlay = class extends google.maps.OverlayView {
                    private pos: google.maps.LatLng;
                    private div: HTMLDivElement | null = null;
                    private html: string;
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
                        const proj = this.getProjection();
                        const px = proj.fromLatLngToDivPixel(this.pos);
                        if (px) {
                            this.div.style.left = (px.x - 26) + 'px';
                            this.div.style.top = (px.y - 26) + 'px';
                        }
                    }
                    onRemove() {
                        if (this.div?.parentNode) this.div.parentNode.removeChild(this.div);
                        this.div = null;
                    }
                    remove() {
                        this.setMap(null);
                    }
                };

                const startPetOverlay = new PetOverlay(
                    new google.maps.LatLng(path[0].lat, path[0].lng),
                    petCircleMarkerHtml('start', petImg),
                );
                startPetOverlay.setMap(mapInstanceRef.current);

                const endPetOverlay = new PetOverlay(
                    new google.maps.LatLng(path[path.length - 1].lat, path[path.length - 1].lng),
                    petCircleMarkerHtml('end', petImg),
                );
                endPetOverlay.setMap(mapInstanceRef.current);

                petMarkersRef.current.push(
                    { setMap: (m: google.maps.Map | null) => { if (m === null) startPetOverlay.setMap(null); } } as unknown as google.maps.Marker,
                    { setMap: (m: google.maps.Map | null) => { if (m === null) endPetOverlay.setMap(null); } } as unknown as google.maps.Marker,
                );
            }

            // Spot markers with click listeners
            if (walk.spots?.length) {
                walk.spots.forEach(spot => {
                    if (spot.latitude == null || spot.longitude == null) return;
                    const pos = new google.maps.LatLng(spot.latitude, spot.longitude);
                    bounds.extend(pos);
                    const imgSrc = spotMarkerImage(spot.type ?? 'OTHER');
                    const marker = new google.maps.Marker({
                        map: mapInstanceRef.current!,
                        position: pos,
                        icon: {
                            url: imgSrc,
                            scaledSize: new google.maps.Size(32, 32),
                            anchor: new google.maps.Point(16, 16),
                        },
                    });
                    spotMarkersRef.current.push(marker);
                    const listener = google.maps.event.addListener(marker, 'click', () => {
                        setSelectedSpot(spot);
                        if (spot.type === 'PHOTO' && spot.imageUrl) resolvePreview(spot);
                        if (mapInstanceRef.current) {
                            mapInstanceRef.current.panTo(pos);
                        }
                    });
                    listenersRef.current.push(listener);
                });
            }

            if (path.length > 1) {
                mapInstanceRef.current.fitBounds(bounds, { top: 80, right: 20, bottom: 80, left: 20 });
            }
        } catch (err) {
            console.error('WalkMapExpand: route drawing failed', err);
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- map drawing effect keyed on mapReady and data; resolvePreview is a stable callback used for route rendering
    }, [mapReady, walk]);

    const formatDate = (dateStr?: string) => {
        if (!dateStr) return '';
        const d = new Date(dateStr);
        return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
    };

    const formatDuration = (seconds?: number) => {
        if (!seconds) return '0분';
        const min = Math.round(seconds / 60);
        return `${min}분`;
    };

    const formatCalories = (cal?: number) => {
        if (!cal) return '0';
        return cal.toLocaleString();
    };

    const closeSheet = () => {
        setSelectedSpot(null);
        clearPreview();
    };

    return (
        <div className="walk_map_expand_page">
            <MainHeader variant="back-only" className="intro_header" />

            {/* Date + share row (same as WalkDetailPage) */}
            {walk && (
                <div className="walk_map_expand_date_row">
                    <span className="walk_detail_date">{formatDate(walk.startTime)}</span>
                    <button
                        type="button"
                        className="walk_detail_share_btn"
                        aria-label="공유"
                        onClick={() => {
                            if (walk) {
                                void share({ kind: 'walk-summary', walkId: Number(walkId), session: walk, watermark: true });
                            }
                        }}
                    >
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                            <rect width="24" height="24" rx="12" fill="#FFE5FA"/>
                            <path d="M6 13.5V16C6 17.1046 6.89543 18 8 18H16C17.1046 18 18 17.1046 18 16V13.5" stroke="#8A497D" strokeWidth="2" strokeLinecap="round"/>
                            <path d="M12 15V6M12 6L8.625 9.375M12 6L15.375 9.375" stroke="#8A497D" strokeWidth="2" strokeLinecap="round"/>
                        </svg>
                    </button>
                </div>
            )}

            {/* Map */}
            <div className="walk_map_expand_map_container">
                {mapLoadFailed ? (
                    <div className="walk_map_expand_map_error">
                        <p>지도를 불러올 수 없습니다</p>
                        <button type="button" onClick={() => window.location.reload()}>새로고침</button>
                    </div>
                ) : (
                    <div ref={mapRef} className="walk_map_expand_map" />
                )}

                {/* Reduction (collapse) button */}
                <button
                    type="button"
                    className="walk_map_expand_reduce_btn"
                    aria-label="지도 축소"
                    onClick={() => navigate(-1)}
                >
                    <svg width="40" height="40" viewBox="16 8 40 40" fill="none">
                        <rect x="16" y="8" width="40" height="40" rx="8" fill="white"/>
                        <path d="M46 18L39 25M26 38L33 31M39 18V25M33 38V31M26 31H33M46 25H39" stroke="#614108" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                </button>

                {/* Location label overlay */}
                <div className="walk_map_expand_location_chip">
                    <img src="/assets/images/common/pin_icon.svg" alt="" />
                    <span>{address}</span>
                </div>
            </div>

            {/* Bottom stats overlay */}
            {walk && (
                <div className="walk_map_expand_stats">
                    <div className="walk_map_expand_stat_item">
                        <span className="walk_map_expand_stat_label">거리(km)</span>
                        <span className="walk_map_expand_stat_value">{walk.distance?.toFixed(1) ?? '0.0'}</span>
                    </div>
                    <div className="walk_map_expand_stat_divider" />
                    <div className="walk_map_expand_stat_item">
                        <span className="walk_map_expand_stat_label">시간</span>
                        <span className="walk_map_expand_stat_value">{formatDuration(walk.durationSeconds)}</span>
                    </div>
                    <div className="walk_map_expand_stat_divider" />
                    <div className="walk_map_expand_stat_item">
                        <span className="walk_map_expand_stat_label">칼로리(Kcal)</span>
                        <span className="walk_map_expand_stat_value">{formatCalories(walk.calories)}</span>
                    </div>
                </div>
            )}

            {/* Spot Detail Bottom Sheet */}
            {selectedSpot && (
                <>
                    <div className="walk_spot_sheet_backdrop" onClick={closeSheet} />
                    <div className="walk_spot_sheet">
                        <div className="walk_spot_sheet_handle" />
                        <div className="walk_spot_sheet_header">
                            <div className="walk_spot_sheet_type">
                                <img className="walk_spot_sheet_icon" src={spotTypeMarkerIcon(selectedSpot.type)} alt="" />
                                <span className="walk_spot_sheet_label">{spotTypeLabel(selectedSpot.type)}</span>
                            </div>
                            <button type="button" className="walk_spot_sheet_close" onClick={closeSheet} aria-label="닫기">
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                    <path d="M18 6L6 18M6 6L18 18" stroke="#8B8B8B" strokeWidth="2" strokeLinecap="round"/>
                                </svg>
                            </button>
                        </div>
                        <div className="walk_spot_sheet_time">
                            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                <circle cx="8" cy="8" r="6.5" stroke="#A58A54" strokeWidth="1.2"/>
                                <path d="M8 4.5V8L10.5 10" stroke="#A58A54" strokeWidth="1.2" strokeLinecap="round"/>
                            </svg>
                            <span>{formatSpotTimestamp(selectedSpot.timestamp)}</span>
                        </div>
                        {/* Non-PHOTO spots: note after timestamp */}
                        {selectedSpot.type !== 'PHOTO' && selectedSpot.note && (
                            <div className="walk_spot_sheet_note">
                                <p>{selectedSpot.note}</p>
                            </div>
                        )}
                        {/* PHOTO spots: photo first, then note below */}
                        {selectedSpot.type === 'PHOTO' && selectedSpot.imageUrl && (
                            <div className="walk_spot_sheet_photo">
                                {previewLoading && (
                                    <div className="walk_spot_sheet_photo_placeholder">
                                        <span>사진 로딩 중...</span>
                                    </div>
                                )}
                                {previewError && (
                                    <div className="walk_spot_sheet_photo_placeholder">
                                        <span>사진을 불러올 수 없습니다</span>
                                    </div>
                                )}
                                {previewUrl && (
                                    <img
                                        src={previewUrl}
                                        alt="산책 사진"
                                        style={{ cursor: 'pointer' }}
                                        onClick={() => { setSelectedSpot(null); openGallery(selectedSpot); }}
                                    />
                                )}
                            </div>
                        )}
                        {selectedSpot.type === 'PHOTO' && selectedSpot.note && (
                            <div className="walk_spot_sheet_note">
                                <p>{selectedSpot.note}</p>
                            </div>
                        )}
                    </div>
                </>
            )}

            {/* Fullscreen photo gallery — shows all walk photos */}
            {galleryUrls.length > 0 && (
                <ImageGalleryModal
                    isOpen={galleryOpen}
                    images={galleryUrls}
                    initialIndex={initialIndex}
                    onClose={closeGallery}
                />
            )}

            <BottomNav />
        </div>
    );
};

export default WalkMapExpandPage;
