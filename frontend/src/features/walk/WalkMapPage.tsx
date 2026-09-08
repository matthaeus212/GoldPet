import { logger } from '../../utils/logger';
import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { loadMapsApi } from '../../hooks/useGoogleMap';
import { nativeBridge } from '../../bridge/nativeBridge';
import apiClient from '../../services/api/client';
import { useAlert } from '../../contexts/AlertContext';
import { MainHeader } from '../../components/common/MainHeader';
import { BottomNav } from '../../components/common/BottomNav';
import './WalkPage.css';

const DEFAULT_LAT = 37.5665;
const DEFAULT_LNG = 126.9780;

export const WalkMapPage = () => {
    const navigate = useNavigate();
    const { showAlert } = useAlert();
    const mapRef = useRef<HTMLDivElement>(null);
    const mapInstanceRef = useRef<google.maps.Map | null>(null);
    const markerRef = useRef<google.maps.Marker | null>(null);
    const [address, setAddress] = useState('위치 확인 중...');
    const [mapLoadFailed, setMapLoadFailed] = useState(false);
    const [stats, setStats] = useState({ totalDistanceKm: 0, totalDurationMinutes: 0, totalCalories: 0, totalGoldEarned: 0 });
    const [isPublic, setIsPublic] = useState(true);
    const [pets, setPets] = useState<Array<{id: number, name: string, profileImageUrl?: string}>>([]);
    const [selectedPetIds, setSelectedPetIds] = useState<Set<number>>(new Set());
    const [petsLoading, setPetsLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;

        const initMap = (lat: number, lng: number) => {
            if (!mapRef.current || mapInstanceRef.current) return;
            try {
                const el = mapRef.current;
                // WebView에서 DOM이 완전히 레이아웃되었는지 확인
                if (el.clientWidth === 0 || el.clientHeight === 0) {
                    logger.debug('[WalkMap] Container has zero size, retrying in 300ms...');
                    setTimeout(() => { if (!cancelled) initMap(lat, lng); }, 300);
                    return;
                }
                logger.debug('[WalkMap] Creating map in container:', el.clientWidth, 'x', el.clientHeight);
                const center = new google.maps.LatLng(lat, lng);
                mapInstanceRef.current = new google.maps.Map(el, {
                    center,
                    zoom: 16,
                    mapTypeControl: false,
                    zoomControl: false,
                    streetViewControl: false,
                    fullscreenControl: false,
                    scaleControl: false,
                    rotateControl: false,
                    keyboardShortcuts: false,
                    gestureHandling: 'greedy',
                });
                markerRef.current = new google.maps.Marker({
                    position: center,
                    map: mapInstanceRef.current,
                });
                logger.debug('[WalkMap] Map created successfully');
            } catch (e) {
                console.error('[WalkMap] Map creation error:', e);
                setMapLoadFailed(true);
            }
        };

        const moveToLocation = (lat: number, lng: number) => {
            if (!mapInstanceRef.current) return;
            const pos = new google.maps.LatLng(lat, lng);
            mapInstanceRef.current.setCenter(pos);
            markerRef.current?.setPosition(pos);
            // Reverse geocode to get address
            apiClient.get(`/maps/reverse-geocode?lat=${lat}&lng=${lng}`)
                .then(res => setAddress(res.data.address))
                .catch(() => {});
        };

        const setupMap = () => {
            if (cancelled) return;
            // Initialize map immediately with default coordinates
            initMap(DEFAULT_LAT, DEFAULT_LNG);

            // Then try to get real location asynchronously
            const getLocation = async () => {
                if (nativeBridge.isAvailable()) {
                    try {
                        const loc = await nativeBridge.callMethod('getCurrentLocation') as { latitude: number; longitude: number };
                        if (!cancelled) moveToLocation(loc.latitude, loc.longitude);
                    } catch {
                        // native bridge failed — keep default location
                    }
                }
            };
            getLocation();
        };

        loadMapsApi()
            .then(() => {
                logger.debug('[WalkMap] Google Maps API ready, initializing with delay for WebView...');
                // WebView에서 DOM 레이아웃이 완료될 시간을 줌
                setTimeout(() => { if (!cancelled) setupMap(); }, 100);
            })
            .catch((e) => {
                console.error('[WalkMap] Failed to load Google Maps API:', e);
                if (!cancelled) setMapLoadFailed(true);
            });

        return () => {
            cancelled = true;
            if (markerRef.current) {
                markerRef.current.setMap(null);
                markerRef.current = null;
            }
            // Google Maps has no destroy() — null the reference for GC
            mapInstanceRef.current = null;
        };
    }, []);

    // Fetch pets
    useEffect(() => {
        apiClient.get('/pets/my')
            .then(res => {
                const data = res.data as Array<{id: number, name: string, profileImageUrl?: string}>;
                setPets(data);
                setSelectedPetIds(new Set(data.map(p => p.id)));
            })
            .catch(() => {})
            .finally(() => setPetsLoading(false));
    }, []);

    const togglePetSelection = (petId: number) => {
        setSelectedPetIds(prev => {
            const next = new Set(prev);
            if (next.has(petId)) {
                if (next.size <= 1) return prev;
                next.delete(petId);
            } else {
                next.add(petId);
            }
            return next;
        });
    };

    // Fetch walk stats
    const fetchStats = useCallback(async () => {
        try {
            const response = await apiClient.get('/walks/my/stats');
            setStats(response.data);
        } catch {
            // Keep default zeros
        }
    }, []);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        fetchStats();
    }, [fetchStats]);

    // 네이티브 산책 완료 후 복귀 시 통계 갱신
    useEffect(() => {
        const handleVisibility = () => {
            if (document.visibilityState === 'visible') {
                fetchStats();
            }
        };
        document.addEventListener('visibilitychange', handleVisibility);
        return () => document.removeEventListener('visibilitychange', handleVisibility);
    }, [fetchStats]);

    return (
        <div id="wrap" className="gp-layout-page">
            <MainHeader variant="back-only" />

            <div id="walkContainer">
                <div className="walk_wrap">
                    {/* 지도 */}
                    <div className="map_box">
                        <div className="map_box_inner">
                            {mapLoadFailed ? (
                                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: '12px' }}>
                                    <p style={{ color: '#8B8B8B', fontSize: '14px' }}>지도를 불러올 수 없습니다</p>
                                    <button
                                        type="button"
                                        onClick={() => window.location.reload()}
                                        style={{ padding: '8px 20px', borderRadius: '20px', border: '1px solid #D4A574', backgroundColor: '#F9ECD2', color: '#614108', fontSize: '13px', cursor: 'pointer' }}
                                    >
                                        새로고침
                                    </button>
                                </div>
                            ) : (
                                <div ref={mapRef} style={{ width: '100%', height: '100%' }} />
                            )}
                        </div>
                        <div className="walk_map_location_chip">
                            <img src="/assets/images/common/pin_icon.svg" alt="" />
                            <span>{address}</span>
                        </div>
                    </div>

                    {/* 반려동물 선택 */}
                    {pets.length > 0 && !petsLoading && (
                        <div className="walk_pet_select">
                            <p className="walk_pet_select_label">반려동물 선택</p>
                            <div className="walk_pet_list_card">
                                <div className="walk_pet_list">
                                    {pets.map(pet => (
                                        <div
                                            key={pet.id}
                                            className={`walk_pet_item ${selectedPetIds.has(pet.id) ? 'selected' : ''}`}
                                            onClick={() => togglePetSelection(pet.id)}
                                        >
                                            <div className="walk_pet_img_wrap">
                                                {pet.profileImageUrl ? (
                                                    <img src={pet.profileImageUrl} alt={pet.name} className="walk_pet_img" />
                                                ) : (
                                                    <div className="walk_pet_img walk_pet_img_placeholder" />
                                                )}
                                            </div>
                                            <span className="walk_pet_name">{pet.name}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}

                    {/* 산책 데이터 */}
                    <div className="walk_data_section">
                        <p className="walk_map_section_title">산책을 시작해보세요!</p>
                        <div className="txt_wrap">
                            <dl>
                                <dt>거리(km)</dt>
                                <dd>{stats.totalDistanceKm.toFixed(1)}</dd>
                            </dl>
                            <dl>
                                <dt>시간(분)</dt>
                                <dd>{stats.totalDurationMinutes.toLocaleString()}</dd>
                            </dl>
                            <dl>
                                <dt>칼로리(Kcal)</dt>
                                <dd>{stats.totalCalories.toLocaleString()}</dd>
                            </dl>
                            <dl>
                                <dt>적립(Gold)</dt>
                                <dd>{stats.totalGoldEarned.toLocaleString()}</dd>
                            </dl>
                        </div>
                    </div>

                    {/* 공개 여부 */}
                    <div className="open_check">
                        <input type="checkbox" id="open" checked={isPublic} onChange={(e) => setIsPublic(e.target.checked)} />
                        <label htmlFor="open">다른 사용자들에게 공개할 지 선택해주세요.</label>
                    </div>
                </div>

                {/* 산책 시작 고정 버튼 */}
                <button type="button" className="walk_start_fixed_btn" onClick={async () => {
                    if (nativeBridge.isAvailable()) {
                        try {
                            await nativeBridge.callMethod('startWalk', { isPublic, petIds: Array.from(selectedPetIds) });
                        } catch {
                            showAlert('산책 시작에 실패했습니다.');
                        }
                    } else {
                        showAlert('모바일 앱에서만 가능한 기능입니다.');
                        navigate('/walk');
                    }
                }}>
                    <img src="/assets/images/walk/play_icon.svg" alt="" />
                    산책 시작
                </button>
            </div>

            <BottomNav />
        </div>
    );
};

export default WalkMapPage;
