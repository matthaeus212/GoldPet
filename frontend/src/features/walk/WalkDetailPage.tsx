import { useState, useEffect, useRef, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { loadMapsApi } from '../../hooks/useGoogleMap';
import { MainHeader } from '../../components/common/MainHeader';
import { BottomNav } from '../../components/common/BottomNav';
import { walkService } from '../../services/walkService';
import type { WalkSpot } from '../../services/walkService';
import { stoolAnalysisService } from '../../services/stoolAnalysisService';
import type { StoolAnalysisResponse } from '../../services/stoolAnalysisService';
import { HealthDetailModal } from '../health/HealthDetailModal';
import { WalkChart, aggregateDaily, aggregateWeekly, aggregateMonthly } from './components/WalkChart';
import type { WalkDataPoint } from './components/WalkChart';
import { useAuthStore } from '../../stores/authStore';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';
import { spotMarkerImage, spotTypeLabel, spotTypeMarkerIcon, formatSpotTimestamp, petCircleMarkerHtml, createPolylinePath } from './utils/walkMapUtils';
import { useWalkPhotoGallery } from './hooks/useWalkPhotoGallery';
import { useShare } from '../share';
import './WalkDetail.css';
import './styles/walkMapMarkers.css';

type Period = 'daily' | 'weekly' | 'monthly';

function getMonday(date: Date): Date {
    const d = new Date(date);
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);
    d.setDate(diff);
    d.setHours(0, 0, 0, 0);
    return d;
}

function formatDate(d: Date): string {
    return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
}

function formatWeekRange(weekStart: Date): string {
    const end = new Date(weekStart);
    end.setDate(end.getDate() + 6);
    return `${formatDate(weekStart)} ~ ${String(end.getMonth() + 1).padStart(2, '0')}.${String(end.getDate()).padStart(2, '0')}`;
}

function formatMonth(year: number, month: number): string {
    return `${year}.${String(month).padStart(2, '0')}`;
}

export const WalkDetailPage = () => {
    const { walkId } = useParams<{ walkId: string }>();
    const navigate = useNavigate();
    const { user: authUser } = useAuthStore();
    const { share } = useShare();

    const [period, setPeriod] = useState<Period>('weekly');
    const [currentDate, setCurrentDate] = useState<Date>(() => {
        const d = new Date();
        d.setHours(0, 0, 0, 0);
        return d;
    });
    const [currentWeekStart, setCurrentWeekStart] = useState<Date>(() => getMonday(new Date()));
    const [currentYear, setCurrentYear] = useState(new Date().getFullYear());
    const [currentMonth, setCurrentMonth] = useState(new Date().getMonth() + 1);
    const chartDateSynced = useRef(false);
    const [selectedSpot, setSelectedSpot] = useState<WalkSpot | null>(null);
    const [selectedAnalysis, setSelectedAnalysis] = useState<StoolAnalysisResponse | null>(null);

    // Sync chart date to viewed walk's date and cross-sync on period change
    const handlePeriodChange = (newPeriod: Period) => {
        // Derive reference date from current period's context
        let refDate: Date;
        if (period === 'daily') {
            refDate = new Date(currentDate);
        } else if (period === 'weekly') {
            refDate = new Date(currentWeekStart);
        } else {
            refDate = new Date(currentYear, currentMonth - 1, 1);
        }

        if (newPeriod === 'daily') {
            refDate.setHours(0, 0, 0, 0);
            setCurrentDate(refDate);
        } else if (newPeriod === 'weekly') {
            setCurrentWeekStart(getMonday(refDate));
        } else {
            setCurrentYear(refDate.getFullYear());
            setCurrentMonth(refDate.getMonth() + 1);
        }
        setPeriod(newPeriod);
    };

    // Walk detail (for route map)
    const { data: walkDetail } = useQuery({
        queryKey: ['walk', 'detail', walkId],
        queryFn: () => walkService.getWalkDetail(Number(walkId)),
        enabled: !!walkId,
    });

    // Sync chart dates to walk's startTime on first load
    useEffect(() => {
        if (!walkDetail?.startTime || chartDateSynced.current) return;
        chartDateSynced.current = true;
        const walkDate = new Date(walkDetail.startTime);
        const d = new Date(walkDate);
        d.setHours(0, 0, 0, 0);
        setCurrentDate(d);
        setCurrentWeekStart(getMonday(walkDate));
        setCurrentYear(walkDate.getFullYear());
        setCurrentMonth(walkDate.getMonth() + 1);
    }, [walkDetail]);

    const {
        resolvedUrls: galleryUrls, galleryOpen, initialIndex,
        previewUrl, previewLoading, previewError,
        openGallery, closeGallery, resolvePreview, clearPreview,
    } = useWalkPhotoGallery(walkDetail?.spots);

    const isOwnWalk = walkDetail?.userId != null && walkDetail.userId === authUser?.id;
    const walkOwnerId = walkDetail?.userId;

    // Own walks: fetch from /walks/my; others: fetch their public walks
    const { data: allWalks = [] } = useQuery({
        queryKey: ['walk', 'chart-data', isOwnWalk ? 'my' : `user-${walkOwnerId}`],
        queryFn: async () => {
            if (isOwnWalk) {
                const { default: apiClient } = await import('../../services/api/client');
                const res = await apiClient.get('/walks/my', { params: { size: 2000 } });
                const data = res.data;
                return Array.isArray(data) ? data : (data.content ?? []);
            } else {
                return walkService.getUserPublicWalksRaw(walkOwnerId!);
            }
        },
        enabled: walkOwnerId != null,
    });

    // Fetch stool analyses for POOP spots - match by imageUrl
    const hasPoopSpotsWithImage = walkDetail?.spots?.some(s => s.type === 'POOP' && s.imageUrl);
    const { data: poopAnalysesPage } = useQuery({
        queryKey: ['health', 'poop-spots', walkDetail?.petIds?.[0]],
        queryFn: () => stoolAnalysisService.getAnalysisHistory(walkDetail!.petIds![0], 0),
        enabled: !!(hasPoopSpotsWithImage && walkDetail?.petIds?.length),
    });
    const analysisMap = useMemo(() => {
        const map = new Map<string, StoolAnalysisResponse>();
        poopAnalysesPage?.content.forEach(a => { if (a.imageUrl) map.set(a.imageUrl, a); });
        return map;
    }, [poopAnalysesPage]);

    // Direct map initialization
    const mapRef = useRef<HTMLDivElement>(null);
    const mapInstanceRef = useRef<google.maps.Map | null>(null);
    const polylineRef = useRef<google.maps.Polyline | null>(null);
    const spotMarkersRef = useRef<google.maps.Marker[]>([]);
    const petMarkersRef = useRef<google.maps.Marker[]>([]);
    const listenersRef = useRef<google.maps.MapsEventListener[]>([]);
    const [mapLoadFailed, setMapLoadFailed] = useState(false);
    const [mapReady, setMapReady] = useState(false);

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
            const lat = walkDetail?.startLatitude ?? 37.5665;
            const lng = walkDetail?.startLongitude ?? 126.9780;
            setTimeout(() => { if (!cancelled) initMap(lat, lng); }, 100);
        };

        loadMapsApi()
            .then(() => {
                if (!cancelled) setup();
            })
            .catch((e) => {
                console.error('[WalkDetail] Failed to load Google Maps API:', e);
                if (!cancelled) setMapLoadFailed(true);
            });

        return () => {
            cancelled = true;
            if (resizeObserver) resizeObserver.disconnect();
            listenersRef.current.forEach(l => {
                try { google.maps.event.removeListener(l); } catch (e) { console.error('WalkDetail: failed to remove listener', e); }
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
            setMapReady(false);
        };
    }, [walkDetail?.startLatitude, walkDetail?.startLongitude]);

    // Draw route, spots, and pet markers when map is ready and walk data loaded
    useEffect(() => {
        if (!mapReady || !mapInstanceRef.current || !walkDetail?.pathPoints?.length) return;
        try {
            // Clean up previous drawings
            if (polylineRef.current) {
                polylineRef.current.setMap(null);
            }
            listenersRef.current.forEach(l => {
                try { google.maps.event.removeListener(l); } catch (e) { console.error('WalkDetail: failed to remove listener', e); }
            });
            listenersRef.current = [];
            spotMarkersRef.current.forEach(m => m.setMap(null));
            spotMarkersRef.current = [];
            petMarkersRef.current.forEach(m => m.setMap(null));
            petMarkersRef.current = [];

            const points = walkDetail.pathPoints
                .filter((p: { lat: number; lng: number }) => p.lat != null && p.lng != null)
                .map((p: { lat: number; lng: number }) => ({ lat: p.lat, lng: p.lng }));
            if (points.length === 0) return;

            // Use Google Maps Polyline
            polylineRef.current = createPolylinePath(mapInstanceRef.current, points, {
                strokeColor: '#6B9A2B',
                strokeWeight: 4,
                strokeOpacity: 0.9,
            });

            // Fit bounds (include path + spots)
            const bounds = new google.maps.LatLngBounds();
            points.forEach(p => bounds.extend(new google.maps.LatLng(p.lat, p.lng)));

            // Pet profile markers at start and end
            if (points.length > 0) {
                const petImg = walkDetail.petProfileImageUrls?.[0];
                const startMarker = new google.maps.Marker({
                    map: mapInstanceRef.current,
                    position: new google.maps.LatLng(points[0].lat, points[0].lng),
                    icon: {
                        url: 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"></svg>'),
                        scaledSize: new google.maps.Size(52, 52),
                        anchor: new google.maps.Point(26, 26),
                    },
                });
                // Use label with custom HTML via OverlayView is complex; use a simple marker with custom icon
                // For pet circle markers, we create a transparent marker and overlay HTML
                const AdvancedMarker = google.maps.marker?.AdvancedMarkerElement;
                const startOverlay = AdvancedMarker
                    ? new AdvancedMarker({
                        map: mapInstanceRef.current,
                        position: new google.maps.LatLng(points[0].lat, points[0].lng),
                    })
                    : startMarker;
                void startOverlay;
                // Fallback: use content-based markers via a helper div
                const startDiv = document.createElement('div');
                startDiv.innerHTML = petCircleMarkerHtml('start', petImg);
                const endDiv = document.createElement('div');
                endDiv.innerHTML = petCircleMarkerHtml('end', petImg);

                // Google Maps Marker doesn't support HTML content directly like Naver.
                // Use a simple transparent icon marker as a positional anchor.
                // The pet circle overlay approach: create custom OverlayView
                startMarker.setMap(null); // remove the placeholder

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
                    new google.maps.LatLng(points[0].lat, points[0].lng),
                    petCircleMarkerHtml('start', petImg),
                );
                startPetOverlay.setMap(mapInstanceRef.current);

                const endPetOverlay = new PetOverlay(
                    new google.maps.LatLng(points[points.length - 1].lat, points[points.length - 1].lng),
                    petCircleMarkerHtml('end', petImg),
                );
                endPetOverlay.setMap(mapInstanceRef.current);

                // Store as markers with setMap interface for cleanup
                petMarkersRef.current.push(
                    { setMap: (m: google.maps.Map | null) => { if (m === null) startPetOverlay.setMap(null); } } as unknown as google.maps.Marker,
                    { setMap: (m: google.maps.Map | null) => { if (m === null) endPetOverlay.setMap(null); } } as unknown as google.maps.Marker,
                );
            }

            // Draw spot markers with click listeners
            if (walkDetail.spots?.length) {
                walkDetail.spots.forEach((spot: WalkSpot) => {
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

            if (points.length > 1) {
                mapInstanceRef.current.fitBounds(bounds, { top: 20, right: 20, bottom: 20, left: 20 });
            } else if (points.length === 1) {
                mapInstanceRef.current.setCenter(new google.maps.LatLng(points[0].lat, points[0].lng));
                mapInstanceRef.current.setZoom(16);
            }
        } catch (err) {
            console.error('WalkDetail: route drawing failed', err);
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- map drawing effect keyed on mapReady and data; resolvePreview is a stable callback used for route rendering
    }, [mapReady, walkDetail]);

    // Prevent body scroll when sheet is open
    useEffect(() => {
        if (selectedSpot) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = '';
        }
        return () => { document.body.style.overflow = ''; };
    }, [selectedSpot]);

    // --- Chart data ---
    const chartData: WalkDataPoint[] = (() => {
        if (period === 'daily') return aggregateDaily(allWalks, currentDate);
        if (period === 'weekly') return aggregateWeekly(allWalks, currentWeekStart);
        return aggregateMonthly(allWalks, currentYear, currentMonth);
    })();

    // --- Date navigation ---
    const navLabel = (() => {
        if (period === 'daily') return formatDate(currentDate);
        if (period === 'weekly') return formatWeekRange(currentWeekStart);
        return formatMonth(currentYear, currentMonth);
    })();

    const goBack = () => {
        if (period === 'daily') {
            const d = new Date(currentDate);
            d.setDate(d.getDate() - 1);
            setCurrentDate(d);
        } else if (period === 'weekly') {
            const d = new Date(currentWeekStart);
            d.setDate(d.getDate() - 7);
            setCurrentWeekStart(d);
        } else {
            let m = currentMonth - 1;
            let y = currentYear;
            if (m < 1) { m = 12; y--; }
            setCurrentMonth(m);
            setCurrentYear(y);
        }
    };

    const goForward = () => {
        if (period === 'daily') {
            const d = new Date(currentDate);
            d.setDate(d.getDate() + 1);
            setCurrentDate(d);
        } else if (period === 'weekly') {
            const d = new Date(currentWeekStart);
            d.setDate(d.getDate() + 7);
            setCurrentWeekStart(d);
        } else {
            let m = currentMonth + 1;
            let y = currentYear;
            if (m > 12) { m = 1; y++; }
            setCurrentMonth(m);
            setCurrentYear(y);
        }
    };

    // --- Summary stats from walkDetail ---
    const distanceKm = walkDetail?.distance ?? 0;
    const calories = walkDetail?.calories ?? 0;
    const gold = Math.round(distanceKm * 10);
    const durationMin = walkDetail?.durationSeconds
        ? Math.round(walkDetail.durationSeconds / 60)
        : 0;

    const detailDate = walkDetail?.startTime
        ? formatDate(new Date(walkDetail.startTime))
        : '';

    const closeSheet = () => {
        setSelectedSpot(null);
        clearPreview();
    };

    return (
        <div className="walk_detail_page">
            <MainHeader variant="back-only" className="intro_header" />

            <div className="walk_detail_container">
                <div className="walk_detail_body">
                    {/* Map Section */}
                    <div>
                        <div className="walk_detail_map_header">
                            <span className="walk_detail_date">{detailDate}</span>
                            <button
                                type="button"
                                className="walk_detail_share_btn"
                                aria-label="공유"
                                onClick={() => {
                                    if (walkDetail) {
                                        void share({ kind: 'walk-summary', walkId: Number(walkId), session: walkDetail, watermark: true });
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

                        <div className="walk_detail_map_box">
                            {mapLoadFailed ? (
                                <div className="walk_detail_map_failed">
                                    <p>지도를 불러올 수 없습니다</p>
                                    <button
                                        type="button"
                                        className="walk_detail_reload_btn"
                                        onClick={() => window.location.reload()}
                                    >
                                        새로고침
                                    </button>
                                </div>
                            ) : (
                                <div ref={mapRef} style={{ width: '100%', height: '100%' }} />
                            )}
                            <button
                                type="button"
                                className="walk_detail_map_expand_btn"
                                aria-label="지도 확대"
                                onClick={() => navigate(`/walk/map-expand/${walkId}`)}
                            >
                                <svg width="40" height="40" viewBox="16 8 40 40" fill="none">
                                    <rect x="16" y="8" width="40" height="40" rx="8" fill="white"/>
                                    <path d="M46 18H39M46 18V25M46 18L39 25M26 38H33M26 38V31M26 38L33 31" stroke="#614108" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                            </button>
                        </div>
                    </div>

                    {/* Distance Chart Card */}
                    <div className="walk_detail_data_card">
                        <p className="walk_detail_data_title">산책 거리(km)</p>

                        {/* Period tabs */}
                        <div className="walk_detail_period_tabs">
                            {(['daily', 'weekly', 'monthly'] as Period[]).map((p) => (
                                <button
                                    key={p}
                                    type="button"
                                    className={`walk_detail_period_tab${period === p ? ' active' : ''}`}
                                    onClick={() => handlePeriodChange(p)}
                                >
                                    {p === 'daily' ? '일' : p === 'weekly' ? '주' : '월'}
                                </button>
                            ))}
                        </div>

                        {/* Date navigation */}
                        <div className="walk_detail_date_nav">
                            <button type="button" className="walk_detail_date_nav_btn" onClick={goBack} aria-label="이전">
                                <svg viewBox="0 0 8 12" fill="none">
                                    <path d="M6 2L2 6L6 10" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                            </button>
                            <span className="walk_detail_date_nav_label">{navLabel}</span>
                            <button type="button" className="walk_detail_date_nav_btn" onClick={goForward} aria-label="다음">
                                <svg viewBox="0 0 8 12" fill="none">
                                    <path d="M2 2L6 6L2 10" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                            </button>
                        </div>

                        {/* Chart */}
                        <div className="walk_detail_chart_area">
                            {chartData.length === 0 ? (
                                <p className="walk_detail_no_data">이 기간에 산책 기록이 없습니다.</p>
                            ) : (
                                <WalkChart period={period} data={chartData} />
                            )}
                        </div>
                    </div>

                    {/* Summary stats */}
                    {walkDetail && (
                        <div className="walk_detail_stats_grid">
                            <div className="walk_detail_stat_card">
                                <span className="walk_detail_stat_label">산책 거리</span>
                                <span className="walk_detail_stat_value">
                                    {distanceKm.toFixed(1)}
                                    <span className="walk_detail_stat_unit">km</span>
                                </span>
                            </div>
                            <div className="walk_detail_stat_card">
                                <span className="walk_detail_stat_label">소요 시간</span>
                                <span className="walk_detail_stat_value">
                                    {durationMin}
                                    <span className="walk_detail_stat_unit">분</span>
                                </span>
                            </div>
                            <div className="walk_detail_stat_card">
                                <span className="walk_detail_stat_label">소요 칼로리</span>
                                <span className="walk_detail_stat_value">
                                    {calories.toLocaleString()}
                                    <span className="walk_detail_stat_unit">Kcal</span>
                                </span>
                            </div>
                            <div className="walk_detail_stat_card">
                                <span className="walk_detail_stat_label">골드 적립</span>
                                <span className="walk_detail_stat_value">
                                    {gold}
                                    <span className="walk_detail_stat_unit">G</span>
                                </span>
                            </div>
                        </div>
                    )}

                    {/* Register as course button */}
                    {(walkDetail?.pathPoints?.length ?? 0) > 0 && (
                        <button
                            type="button"
                            onClick={() => navigate(`/courses/create/from-walk/${walkId}`)}
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                gap: '8px',
                                width: '100%',
                                padding: '14px 0',
                                marginTop: '16px',
                                background: '#fff',
                                border: '1.5px solid #614108',
                                borderRadius: '12px',
                                color: '#614108',
                                fontSize: '15px',
                                fontWeight: '600',
                                cursor: 'pointer',
                            }}
                        >
                            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                                <path d="M3 15C5 15 5 10 8 10C11 10 11 15 14 15C17 15 17 10 17 10" stroke="#614108" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                                <circle cx="3" cy="15" r="1.5" fill="#614108"/>
                                <circle cx="17" cy="10" r="1.5" fill="#614108"/>
                            </svg>
                            이 산책을 코스로 등록
                        </button>
                    )}
                </div>
            </div>

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
                        {/* POOP spots: show analysis badge if available */}
                        {selectedSpot.type === 'POOP' && selectedSpot.imageUrl && (() => {
                            const analysis = analysisMap.get(selectedSpot.imageKey ?? selectedSpot.imageUrl!);
                            if (!analysis || analysis.status !== 'COMPLETED') return null;
                            const color = analysis.overallScore != null
                                ? (analysis.overallScore <= 2 ? '#FF5005' : analysis.overallScore === 3 ? '#F5A623' : '#4CAF50')
                                : '#AAAAAA';
                            return (
                                <div style={{ padding: '12px 16px 0' }}>
                                    <button
                                        type="button"
                                        onClick={() => { setSelectedSpot(null); setSelectedAnalysis(analysis); }}
                                        style={{
                                            display: 'flex', alignItems: 'center', gap: 10,
                                            width: '100%', padding: '12px', borderRadius: 12,
                                            background: '#FFF7E6', border: '1.5px solid #EFE1C4',
                                            cursor: 'pointer',
                                        }}
                                    >
                                        <span style={{
                                            width: 36, height: 36, borderRadius: '50%', background: color,
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            color: '#fff', fontWeight: 700, fontSize: 15, flexShrink: 0,
                                        }}>
                                            {analysis.overallScore}
                                        </span>
                                        <span style={{ flex: 1, textAlign: 'left' }}>
                                            <span style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#614108' }}>AI 건강 분석 결과 보기</span>
                                            {analysis.healthSummary && (
                                                <span style={{ display: 'block', fontSize: 12, color: '#A58A54', marginTop: 2,
                                                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 220 }}>
                                                    {analysis.healthSummary}
                                                </span>
                                            )}
                                        </span>
                                        <svg width="7" height="12" viewBox="0 0 7 12" fill="none">
                                            <path d="M1 1l5 5-5 5" stroke="#AAAAAA" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                                        </svg>
                                    </button>
                                </div>
                            );
                        })()}
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

            {/* Health detail modal from POOP spot analysis */}
            {selectedAnalysis && (
                <HealthDetailModal
                    analysis={selectedAnalysis}
                    petName={walkDetail?.petNames?.[0] ?? ''}
                    petImageUrl={walkDetail?.petProfileImageUrls?.[0] ?? null}
                    onClose={() => setSelectedAnalysis(null)}
                />
            )}

            <BottomNav />
        </div>
    );
};

export default WalkDetailPage;
