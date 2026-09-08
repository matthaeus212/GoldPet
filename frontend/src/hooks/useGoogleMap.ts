import { logger } from '../utils/logger';
import { useRef, useEffect, useState } from 'react';
import { setOptions, importLibrary } from '@googlemaps/js-api-loader';
import { nativeBridge } from '../bridge/nativeBridge';
import apiClient from '../services/api/client';

const DEFAULT_LAT = 37.5665;
const DEFAULT_LNG = 126.9780;

let optionsSet = false;
let mapsApiPromise: Promise<typeof google.maps> | null = null;

export function loadMapsApi(): Promise<typeof google.maps> {
    if (!optionsSet) {
        setOptions({ key: import.meta.env.VITE_GOOGLE_MAPS_KEY || '', v: 'weekly' });
        optionsSet = true;
    }
    if (!mapsApiPromise) {
        // reject 시 캐시된 rejected promise가 모든 지도 페이지를 영구 차단하므로 실패 시 리셋
        mapsApiPromise = importLibrary('maps').then(() => google.maps).catch((err) => {
            mapsApiPromise = null;
            throw err;
        });
    }
    return mapsApiPromise;
}

interface UseGoogleMapOptions {
    initialLat?: number;
    initialLng?: number;
    zoom?: number;
    withMarker?: boolean;
    withAddressLookup?: boolean;
    skipGeolocation?: boolean;
}

interface UseGoogleMapResult {
    mapRef: React.RefObject<HTMLDivElement>;
    mapInstanceRef: React.RefObject<google.maps.Map | null>;
    markerRef: React.RefObject<google.maps.Marker | null>;
    address: string;
    mapLoadFailed: boolean;
    moveToLocation: (lat: number, lng: number) => void;
}

export function useGoogleMap(options: UseGoogleMapOptions = {}): UseGoogleMapResult {
    const {
        initialLat = DEFAULT_LAT,
        initialLng = DEFAULT_LNG,
        zoom = 16,
        withMarker = true,
        withAddressLookup = false,
        skipGeolocation = false,
    } = options;

    const mapRef = useRef<HTMLDivElement>(null);
    const mapInstanceRef = useRef<google.maps.Map | null>(null);
    const markerRef = useRef<google.maps.Marker | null>(null);
    const [address, setAddress] = useState('위치 확인 중...');
    const [mapLoadFailed, setMapLoadFailed] = useState(false);

    const moveToLocation = (lat: number, lng: number) => {
        if (!mapInstanceRef.current) return;
        const pos = new google.maps.LatLng(lat, lng);
        mapInstanceRef.current.setCenter(pos);
        if (markerRef.current) {
            markerRef.current.setPosition(pos);
        }
        if (withAddressLookup) {
            apiClient.get(`/maps/reverse-geocode?lat=${lat}&lng=${lng}`)
                .then(res => setAddress(res.data.address))
                .catch(() => {});
        }
    };

    useEffect(() => {
        let cancelled = false;

        const initMap = (lat: number, lng: number) => {
            if (!mapRef.current || mapInstanceRef.current) return;
            const el = mapRef.current;
            // WebView에서 DOM이 완전히 레이아웃되었는지 확인 (zero-size retry)
            if (el.clientWidth === 0 || el.clientHeight === 0) {
                logger.debug('[useGoogleMap] Container has zero size, retrying in 300ms...');
                setTimeout(() => { if (!cancelled) initMap(lat, lng); }, 300);
                return;
            }
            try {
                logger.debug('[useGoogleMap] Creating map:', el.clientWidth, 'x', el.clientHeight);
                const center = new google.maps.LatLng(lat, lng);
                mapInstanceRef.current = new google.maps.Map(el, {
                    center,
                    zoom,
                    mapTypeControl: false,
                    zoomControl: false,
                    streetViewControl: false,
                    fullscreenControl: false,
                    scaleControl: false,
                    rotateControl: false,
                    keyboardShortcuts: false,
                    gestureHandling: 'greedy',
                });
                if (withMarker) {
                    markerRef.current = new google.maps.Marker({
                        position: center,
                        map: mapInstanceRef.current,
                    });
                }
                logger.debug('[useGoogleMap] Map created successfully');
            } catch (e) {
                console.error('[useGoogleMap] Map creation error:', e);
                setMapLoadFailed(true);
            }
        };

        const setupMap = (lat: number, lng: number) => {
            if (cancelled) return;
            initMap(lat, lng);

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
            if (!skipGeolocation) {
                getLocation();
            }
        };

        loadMapsApi()
            .then(() => {
                if (!cancelled) {
                    // WebView에서 DOM 레이아웃이 완료될 시간을 줌
                    setTimeout(() => { if (!cancelled) setupMap(initialLat, initialLng); }, 100);
                }
            })
            .catch((e) => {
                console.error('[useGoogleMap] Failed to load Google Maps API:', e);
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
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return { mapRef: mapRef as React.RefObject<HTMLDivElement>, mapInstanceRef, markerRef, address, mapLoadFailed, moveToLocation };
}
