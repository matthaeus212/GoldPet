import { useRef, useEffect, useState, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { loadMapsApi } from '../../hooks/useGoogleMap';
import { MainHeader } from '../../components/common/MainHeader';
import {
  courseService,
  type CreateCourseRequest,
  type UpdateCourseRequest,
  type CourseSpotDto,
  type CourseSpotType,
  type CourseDifficulty,
} from '../../services/courseService';
import { walkService } from '../../services/walkService';
import { SPOT_TYPES, spotLabel } from './utils/courseHelpers';
import { SpotIcon, spotIconUrl } from './components/SpotIcon';
import { createPolylinePath } from '../walk/utils/walkMapUtils';
import { useAlert } from '../../contexts/AlertContext';
import { useShare } from '../share';
import './CourseCreatePage.css';
import { nativeBridge } from '../../bridge/nativeBridge';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import { useOverlayColor } from '../../hooks/useOverlayColor';

// ── Walk spot type → Course spot type mapping ────────────────────────────────

function walkSpotTypeToCourseSpotType(
  walkType: string,
): CourseSpotType | null {
  switch (walkType) {
    case 'PHOTO': return 'PHOTO_SPOT';
    case 'PROBLEM': return 'DANGER_ZONE';
    default: return null; // PEE / POOP not mapped
  }
}

// ── Gold reward calculation — mirrors backend: WalkService.kt:283-291 ────────

function calculateWalkGold(distanceKm: number): number {
  if (distanceKm < 0.5) return 0;
  if (distanceKm < 1.0) return 1;
  if (distanceKm < 3.0) return 3;
  if (distanceKm < 5.0) return 5;
  return 10;
}

// ── Haversine distance (km) between two [lat,lng] points ─────────────────────

function haversine(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371;
  const dLat = (lat2 - lat1) * (Math.PI / 180);
  const dLng = (lng2 - lng1) * (Math.PI / 180);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * (Math.PI / 180)) *
    Math.cos(lat2 * (Math.PI / 180)) *
    Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function calcTotalDistance(waypoints: [number, number][]): number {
  let total = 0;
  for (let i = 1; i < waypoints.length; i++) {
    total += haversine(waypoints[i - 1][0], waypoints[i - 1][1], waypoints[i][0], waypoints[i][1]);
  }
  return Math.round(total * 100) / 100;
}

// ── SVG circle icon as data URI for Google Maps Marker ───────────────────────

function circleIconUrl(color: string, size: number): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}"><circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 2}" fill="${color}" stroke="#fff" stroke-width="2"/></svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

// ── Local spot type (before saving) ─────────────────────────────────────────

interface LocalSpot {
  id: string; // temp key
  serverId?: number;
  lat: number;
  lng: number;
  type: CourseSpotType;
  name: string;
  description: string;
  imageUrl?: string;
}

// ── SpotModal component ───────────────────────────────────────────────────────

interface SpotModalProps {
  onConfirm: (type: CourseSpotType, name: string, description: string) => void;
  onClose: () => void;
  editSpot?: LocalSpot;
  spots: LocalSpot[];
}

function SpotModal({ onConfirm, onClose, editSpot, spots }: SpotModalProps) {
  const [selectedType, setSelectedType] = useState<CourseSpotType | null>(editSpot?.type ?? null);
  const [name, setName] = useState(editSpot?.name ?? '');
  const [description, setDescription] = useState(editSpot?.description ?? '');

  const handleConfirm = () => {
    if (!selectedType) return;
    onConfirm(selectedType, name.trim(), description.trim());
  };

  return (
    <div className="course_create_spot_modal">
      <div className="course_create_spot_modal_backdrop" onClick={onClose} />
      <div className="course_create_spot_modal_content">
        <div className="course_create_spot_modal_handle" />
        <p className="course_create_spot_modal_title">{editSpot ? '스팟 수정' : '스팟 추가'}</p>

        <div className="course_create_spot_types">
          {SPOT_TYPES.map(s => {
            const count = spots.filter(sp => sp.type === s.type).length;
            return (
              <button
                key={s.type}
                className={`course_create_spot_type_btn${selectedType === s.type ? ' selected' : ''}`}
                onClick={() => setSelectedType(s.type)}
                type="button"
              >
                <div className="course_create_spot_type_icon">
                  <SpotIcon type={s.type} size={28} />
                  {count > 0 && (
                    <span className="course_create_spot_type_count">{count}</span>
                  )}
                </div>
                <span className="course_create_spot_type_label">{s.label}</span>
              </button>
            );
          })}
        </div>

        <div className="course_create_spot_modal_fields">
          <input
            className="course_create_input"
            placeholder="이름을 적어주세요"
            value={name}
            onChange={e => setName(e.target.value)}
            maxLength={50}
          />
          <input
            className="course_create_input"
            placeholder="설명을 적어주세요"
            value={description}
            onChange={e => setDescription(e.target.value)}
            maxLength={200}
          />
        </div>

        <button
          className="course_create_spot_modal_confirm"
          onClick={handleConfirm}
          disabled={!selectedType}
          type="button"
        >
          {editSpot ? '수정 완료' : '지도에 추가'}
        </button>
      </div>
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export function CourseCreatePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showAlert, showConfirm } = useAlert();
  const { share } = useShare();
  const { walkId: walkIdParam, courseId: courseIdParam } = useParams<{ walkId: string; courseId: string }>();
  const walkId = walkIdParam ? parseInt(walkIdParam, 10) : undefined;
  const isFromWalk = walkId != null && !isNaN(walkId);
  const courseId = courseIdParam ? parseInt(courseIdParam, 10) : undefined;
  const isEditMode = courseId != null && !isNaN(courseId);

  // Map refs
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<google.maps.Map | null>(null);
  const polylineRef = useRef<google.maps.Polyline | null>(null);
  const waypointMarkersRef = useRef<google.maps.Marker[]>([]);
  const spotMarkersRef = useRef<Map<string, google.maps.Marker>>(new Map());
  const clickListenerRef = useRef<google.maps.MapsEventListener[]>([]);
  const [mapReady, setMapReady] = useState(false);

  // State
  const [waypoints, setWaypoints] = useState<[number, number][]>([]);
  const [spots, setSpots] = useState<LocalSpot[]>([]);
  const [lastTapPos, setLastTapPos] = useState<{ lat: number; lng: number } | null>(null);
  const [showSpotModal, setShowSpotModal] = useState(false);
  const [editingSpot, setEditingSpot] = useState<LocalSpot | null>(null);
  const [mapLoadFailed, setMapLoadFailed] = useState(false);
  const modalOpenRef = useRef(false);
  const lastClickTimeRef = useRef(0);
  const lastPinchTimeRef = useRef(0);

  // Form state
  const [title, setTitle] = useState('');
  const [desc, setDesc] = useState('');
  const [difficulty, setDifficulty] = useState<CourseDifficulty>('EASY');
  const [estimatedMinutes, setEstimatedMinutes] = useState('');
  const [distanceKm, setDistanceKm] = useState(0);

  // ── Fetch walk detail when in from-walk mode ──────────────────────────────

  const { data: walkDetail, isLoading: walkLoading } = useQuery({
    queryKey: ['walkDetail', walkId],
    queryFn: () => walkService.getWalkDetail(walkId!),
    enabled: isFromWalk,
  });

  // ── Fetch course detail when in edit mode ────────────────────────────────

  const { data: courseDetail, isLoading: courseLoading } = useQuery({
    queryKey: ['course', 'detail', courseId],
    queryFn: () => courseService.getCourseDetail(courseId!),
    enabled: isEditMode,
  });

  // Pre-fill from existing course data
  const courseFilledRef = useRef(false);
  useEffect(() => {
    if (!courseDetail || courseFilledRef.current) return;
    courseFilledRef.current = true;

    setTitle(courseDetail.title);
    setDesc(courseDetail.description ?? '');
    setDifficulty(courseDetail.difficulty);
    setEstimatedMinutes(String(courseDetail.estimatedMinutes));
    setDistanceKm(courseDetail.distanceKm);

    if (courseDetail.path && courseDetail.path.length > 0) {
      const pts: [number, number][] = courseDetail.path.map(p => [p[0], p[1]]);
      setWaypoints(pts);
    }

    if (courseDetail.spots && courseDetail.spots.length > 0) {
      const mappedSpots: LocalSpot[] = courseDetail.spots.map((s, i) => ({
        id: `edit-spot-${i}`,
        serverId: s.id,
        lat: s.latitude,
        lng: s.longitude,
        type: s.type as CourseSpotType,
        name: s.name ?? '',
        description: s.description ?? '',
        imageUrl: s.imageUrl ?? undefined,
      }));
      setSpots(mappedSpots);
    }
  }, [courseDetail]);

  // Pre-fill from walk data
  useEffect(() => {
    if (!walkDetail) return;

    // Convert pathPoints to waypoints array
    if (walkDetail.pathPoints && walkDetail.pathPoints.length > 0) {
      const pts: [number, number][] = walkDetail.pathPoints.map(p => [p.lat, p.lng]);
      setWaypoints(pts);
      setDistanceKm(walkDetail.distance ?? 0);
    }

    // Pre-fill estimated minutes from duration
    if (walkDetail.durationSeconds) {
      setEstimatedMinutes(String(Math.round(walkDetail.durationSeconds / 60)));
    }

    // Map walk spots to course spots
    if (walkDetail.spots && walkDetail.spots.length > 0) {
      const mappedSpots: LocalSpot[] = walkDetail.spots
        .map((ws, i) => {
          const courseType = walkSpotTypeToCourseSpotType(ws.type);
          if (!courseType) return null;
          return {
            id: `walk-spot-${i}`,
            lat: ws.latitude,
            lng: ws.longitude,
            type: courseType,
            name: ws.note ?? '',
            description: '',
            imageUrl: ws.imageUrl,
          } as LocalSpot;
        })
        .filter((s): s is LocalSpot => s !== null);
      setSpots(mappedSpots);
    }
  }, [walkDetail]);

  // Recalculate distance when waypoints change (unless initial pre-fill)
  const userEditedPathRef = useRef(false);
  useEffect(() => {
    // Skip recalculation until pre-fill is done
    if (isFromWalk && walkDetail && !userEditedPathRef.current) return;
    if (isEditMode && !courseFilledRef.current) return;
    // After pre-fill, skip one more cycle (the waypoints set by pre-fill)
    if (isEditMode && courseFilledRef.current && !userEditedPathRef.current) {
      userEditedPathRef.current = true;
      return;
    }
    if (isFromWalk && !userEditedPathRef.current) {
      userEditedPathRef.current = true;
      return;
    }
    setDistanceKm(calcTotalDistance(waypoints));
  }, [waypoints, isFromWalk, walkDetail, isEditMode]);

  // ── Map initialization ────────────────────────────────────────────────────

  const redrawPolyline = useCallback((pts: [number, number][]) => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (polylineRef.current) {
      polylineRef.current.setMap(null);
      polylineRef.current = null;
    }
    if (pts.length < 2) return;

    const path = pts.map(([lat, lng]) => ({ lat, lng }));
    polylineRef.current = createPolylinePath(map, path, {
      strokeColor: '#614108',
      strokeWeight: 4,
      strokeOpacity: 0.85,
    });
  }, []);

  const redrawWaypointMarkers = useCallback((pts: [number, number][]) => {
    const map = mapInstanceRef.current;
    if (!map) return;

    for (const m of waypointMarkersRef.current) m.setMap(null);
    waypointMarkersRef.current = [];

    pts.forEach((pt, idx) => {
      const isFirst = idx === 0;
      const isLast = idx === pts.length - 1 && pts.length > 1;
      let color: string;
      let size: number;
      if (isFirst) {
        color = '#614108';
        size = 14;
      } else if (isLast) {
        color = '#E74C3C';
        size = 14;
      } else {
        color = '#c8a96e';
        size = 10;
      }

      const marker = new google.maps.Marker({
        map,
        position: new google.maps.LatLng(pt[0], pt[1]),
        icon: {
          url: circleIconUrl(color, size),
          scaledSize: new google.maps.Size(size, size),
          anchor: new google.maps.Point(size / 2, size / 2),
        },
        draggable: true,
      });

      // Tap marker to remove it
      google.maps.event.addListener(marker, 'click', () => {
        setWaypoints(prev => {
          const next = prev.filter((_, i) => i !== idx);
          return next;
        });
      });

      // Drag marker to update coordinates
      google.maps.event.addListener(marker, 'dragend', () => {
        const pos = marker.getPosition();
        if (!pos) return;
        setWaypoints(prev => prev.map((p, i) =>
          i === idx ? [pos.lat(), pos.lng()] as [number, number] : p
        ));
      });

      waypointMarkersRef.current.push(marker);
    });
  }, []);

  const addSpotMarker = useCallback((spot: LocalSpot) => {
    const map = mapInstanceRef.current;
    if (!map) return;

    const iconSrc = spotIconUrl(spot.type);
    const marker = new google.maps.Marker({
      map,
      position: new google.maps.LatLng(spot.lat, spot.lng),
      icon: {
        url: iconSrc,
        scaledSize: new google.maps.Size(32, 32),
        anchor: new google.maps.Point(16, 16),
      },
      draggable: true,
    });

    // Drag marker to update spot coordinates
    google.maps.event.addListener(marker, 'dragend', () => {
      const pos = marker.getPosition();
      if (!pos) return;
      setSpots(prev => prev.map(s =>
        s.id === spot.id ? { ...s, lat: pos.lat(), lng: pos.lng() } : s
      ));
    });

    spotMarkersRef.current.set(spot.id, marker);
  }, []);

  const removeSpotMarker = useCallback((spotId: string) => {
    const marker = spotMarkersRef.current.get(spotId);
    if (marker) {
      marker.setMap(null);
      spotMarkersRef.current.delete(spotId);
    }
  }, []);

  // Sync waypoints to map + fitBounds once for edit/from-walk modes
  const initialFitDoneRef = useRef(false);
  useEffect(() => {
    if (!mapReady) return;
    redrawPolyline(waypoints);
    redrawWaypointMarkers(waypoints);

    // Fit bounds once when pre-filled waypoints are first drawn
    if (!initialFitDoneRef.current && (isEditMode || isFromWalk)) {
      const map = mapInstanceRef.current;
      if (map && waypoints.length >= 2) {
        initialFitDoneRef.current = true;
        const bounds = new google.maps.LatLngBounds();
        for (const [lat, lng] of waypoints) bounds.extend({ lat, lng });
        map.fitBounds(bounds, { top: 30, right: 30, bottom: 30, left: 30 });
      } else if (map && waypoints.length === 1) {
        initialFitDoneRef.current = true;
        map.setCenter({ lat: waypoints[0][0], lng: waypoints[0][1] });
      }
    }
  }, [mapReady, waypoints, redrawPolyline, redrawWaypointMarkers, isEditMode, isFromWalk]);

  const initMap = useCallback(() => {
    if (!mapRef.current || mapInstanceRef.current) return;
    const el = mapRef.current;
    if (el.clientWidth === 0 || el.clientHeight === 0) {
      setTimeout(initMap, 300);
      return;
    }

    const defaultCenter = new google.maps.LatLng(37.5665, 126.9780);
    try {
      mapInstanceRef.current = new google.maps.Map(el, {
        center: defaultCenter,
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

      // Track pinch-to-zoom via zoom_changed to filter gesture-related events
      const zoomListener = google.maps.event.addListener(
        mapInstanceRef.current, 'zoom_changed', () => {
          lastPinchTimeRef.current = Date.now();
        },
      );

      // Map click handler — add waypoint or open spot modal
      const handleMapClick = (e: google.maps.MapMouseEvent) => {
        // Ignore clicks while modal is open (Android leaks tap events through modal)
        if (modalOpenRef.current) return;
        // Debounce: prevent double-fire
        const now = Date.now();
        if (now - lastClickTimeRef.current < 300) return;
        // Ignore clicks during/after pinch-to-zoom gesture
        if (now - lastPinchTimeRef.current < 400) return;
        lastClickTimeRef.current = now;

        if (!e.latLng) return;
        const lat = e.latLng.lat();
        const lng = e.latLng.lng();
        setLastTapPos({ lat, lng });
        setWaypoints(prev => [...prev, [lat, lng]]);
      };

      const clickL = google.maps.event.addListener(mapInstanceRef.current, 'click', handleMapClick);
      clickListenerRef.current = [clickL, zoomListener];

      // Try to get current position
      const setCenter = (lat: number, lng: number) => {
        if (!mapInstanceRef.current) return;
        mapInstanceRef.current.setCenter(new google.maps.LatLng(lat, lng));
      };

      const getLocation = async () => {
        if (nativeBridge.isAvailable()) {
          try {
            const loc = await nativeBridge.callMethod('getCurrentLocation') as { latitude: number; longitude: number };
            setCenter(loc.latitude, loc.longitude);
          } catch {
            // native bridge failed — keep default location
          }
        }
      };
      if (!isFromWalk && !isEditMode) getLocation();
    } catch (e) {
      console.error('[CourseCreatePage] map init failed:', e);
      setMapLoadFailed(true);
    }
  }, [isFromWalk, isEditMode]);

  useEffect(() => {
    if (walkLoading || courseLoading) return;
    let cancelled = false;

    loadMapsApi()
      .then(() => {
        if (!cancelled) {
          setTimeout(() => { if (!cancelled) initMap(); }, 100);
        }
      })
      .catch((e) => {
        console.error('[CourseCreatePage] Failed to load Google Maps API:', e);
        if (!cancelled) setMapLoadFailed(true);
      });

    return () => {
      cancelled = true;
      for (const l of clickListenerRef.current) {
        try { google.maps.event.removeListener(l); } catch (e) { console.error('CourseCreate: failed to remove listener', e); }
      }
      clickListenerRef.current = [];
      for (const m of waypointMarkersRef.current) m.setMap(null);
      waypointMarkersRef.current = [];
      // eslint-disable-next-line react-hooks/exhaustive-deps -- spotMarkersRef.current is populated by initMap after effect runs; capturing at effect start would miss markers
      for (const m of spotMarkersRef.current.values()) m.setMap(null);
      spotMarkersRef.current.clear();
      if (polylineRef.current) {
        polylineRef.current.setMap(null);
        polylineRef.current = null;
      }
      // Google Maps has no destroy() — null the reference for GC
      initialFitDoneRef.current = false;
      mapInstanceRef.current = null;
      setMapReady(false);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [walkLoading, courseLoading]);

  // Add spot markers when spots change
  const prevSpotsRef = useRef<LocalSpot[]>([]);
  useEffect(() => {
    if (!mapReady) return;

    const prevIds = new Set(prevSpotsRef.current.map(s => s.id));
    const currIds = new Set(spots.map(s => s.id));

    // Add new spots
    for (const spot of spots) {
      if (!prevIds.has(spot.id)) addSpotMarker(spot);
    }
    // Update moved spots in-place (no flicker)
    for (const spot of spots) {
      if (prevIds.has(spot.id)) {
        const prev = prevSpotsRef.current.find(p => p.id === spot.id);
        if (prev && (prev.lat !== spot.lat || prev.lng !== spot.lng)) {
          const marker = spotMarkersRef.current.get(spot.id);
          if (marker) {
            marker.setPosition(new google.maps.LatLng(spot.lat, spot.lng));
          }
        }
      }
    }
    // Remove deleted spots
    for (const prev of prevSpotsRef.current) {
      if (!currIds.has(prev.id)) removeSpotMarker(prev.id);
    }

    prevSpotsRef.current = spots;
  }, [mapReady, spots, addSpotMarker, removeSpotMarker]);

  // ── Spot modal handling ───────────────────────────────────────────────────

  const handleAddSpot = () => {
    // Determine spot position: last waypoint, last tap, or map center
    let pos: { lat: number; lng: number } | null = null;
    if (waypoints.length > 0) {
      const last = waypoints[waypoints.length - 1];
      pos = { lat: last[0], lng: last[1] };
    } else if (lastTapPos) {
      pos = lastTapPos;
    } else if (mapInstanceRef.current) {
      const center = mapInstanceRef.current.getCenter();
      if (center) {
        pos = { lat: center.lat(), lng: center.lng() };
      }
    }
    if (pos) {
      setLastTapPos(pos);
      modalOpenRef.current = true;
      setShowSpotModal(true);
    }
  };

  const closeModal = () => {
    setShowSpotModal(false);
    setEditingSpot(null);
    // Delay clearing modalOpenRef to absorb leaked tap events on Android
    setTimeout(() => { modalOpenRef.current = false; }, 400);
  };

  const handleSpotConfirm = (type: CourseSpotType, name: string, description: string) => {
    if (editingSpot) {
      setSpots(prev => prev.map(s =>
        s.id === editingSpot.id ? { ...s, type, name, description } : s
      ));
      closeModal();
      return;
    }

    const pos = lastTapPos ?? (waypoints.length > 0
      ? { lat: waypoints[waypoints.length - 1][0], lng: waypoints[waypoints.length - 1][1] }
      : null);
    if (!pos) return;

    const newSpot: LocalSpot = {
      id: `spot-${Date.now()}`,
      lat: pos.lat,
      lng: pos.lng,
      type,
      name,
      description,
    };
    setSpots(prev => [...prev, newSpot]);
    closeModal();
  };

  const handleRemoveSpot = (spotId: string) => {
    setSpots(prev => prev.filter(s => s.id !== spotId));
  };

  // ── Reset all (초기화 button) ───────────────────────────────────────────

  const handleResetAll = () => {
    showConfirm('모든 입력을 초기화하시겠습니까?', () => {
      setWaypoints([]);
      setSpots([]);
      setTitle('');
      setDesc('');
      setDifficulty('EASY');
      setEstimatedMinutes('');
      setDistanceKm(0);
      setLastTapPos(null);
    });
  };

  // ── Submit ────────────────────────────────────────────────────────────────

  const createMutation = useMutation({
    mutationFn: (req: CreateCourseRequest) =>
      isFromWalk
        ? courseService.createCourseFromWalk(walkId!, req)
        : courseService.createCourse(req),
    onSuccess: (data) => {
      navigate(`/courses/${data.id}`, { replace: true });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (req: UpdateCourseRequest) =>
      courseService.updateCourse(courseId!, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['course'] });
      queryClient.invalidateQueries({ queryKey: ['courses'] });
      navigate(`/courses/${courseId}`, { replace: true });
    },
  });

  const activeMutation = isEditMode ? updateMutation : createMutation;

  const handleSubmit = () => {
    if (!title.trim()) {
      showAlert('코스 제목을 입력해주세요.');
      return;
    }
    if (waypoints.length < 2) {
      showAlert('지도에 경로 지점을 2개 이상 추가해주세요.');
      return;
    }
    if (!estimatedMinutes || parseInt(estimatedMinutes, 10) <= 0) {
      showAlert('예상 소요 시간을 입력해주세요.');
      return;
    }
    if (distanceKm <= 0) {
      showAlert('경로 거리가 0입니다. 경로 지점을 더 넓게 배치해주세요.');
      return;
    }

    const spotDtos: CourseSpotDto[] = spots.map((s, i) => ({
      id: s.serverId || undefined,
      latitude: s.lat,
      longitude: s.lng,
      type: s.type,
      name: s.name || undefined,
      description: s.description || undefined,
      imageUrl: s.imageUrl || undefined,
      orderIndex: i,
    }));

    if (isEditMode) {
      const req: UpdateCourseRequest = {
        title: title.trim(),
        description: desc.trim() || undefined,
        path: waypoints,
        distanceKm,
        estimatedMinutes: parseInt(estimatedMinutes, 10) || 0,
        difficulty,
        spots: spotDtos,
      };
      updateMutation.mutate(req);
    } else {
      const req: CreateCourseRequest = {
        title: title.trim(),
        description: desc.trim() || undefined,
        path: waypoints,
        distanceKm,
        estimatedMinutes: parseInt(estimatedMinutes, 10) || 0,
        difficulty,
        spots: spotDtos,
        originWalkId: isFromWalk ? walkId : undefined,
      };
      createMutation.mutate(req);
    }
  };

  // ── Scroll focused input into view (iOS/Android keyboard) ────────────────
  const handleInputFocus = (e: React.FocusEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setTimeout(() => {
      e.target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 300);
  };

  // ── Dismiss keyboard on tap outside inputs ────────────────────────────────
  const keyboardDismiss = useKeyboardDismiss();
  useOverlayColor(showSpotModal, '#000000', '#EFE1C4');

  // ── Render ────────────────────────────────────────────────────────────────

  if (walkLoading || courseLoading) {
    return (
      <div className="course_create_page course_create_loading">
        <p className="course_create_loading_text">
          {isEditMode ? '코스 정보 불러오는 중...' : '산책 기록 불러오는 중...'}
        </p>
      </div>
    );
  }

  // Format walk date for display
  const walkDate = walkDetail?.startTime
    ? new Date(walkDetail.startTime).toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\. /g, '.').replace(/\.$/, '')
    : '';
  const walkMinutes = walkDetail?.durationSeconds ? Math.round(walkDetail.durationSeconds / 60) : 0;
  const walkCalories = walkDetail?.calories ?? 0;
  const walkGold = walkDetail ? calculateWalkGold(walkDetail.distance) : 0;

  const handleShare = async () => {
    const resolvedCourseId = courseId ?? walkId;
    if (resolvedCourseId == null) return;
    await share({
      kind: 'course',
      courseId: resolvedCourseId,
      title: title || '산책 코스',
    });
  };

  return (
    <div className="course_create_page" {...keyboardDismiss}>
      {/* Header */}
      <MainHeader variant="back-only" className="intro_header" />

      {/* Date row — from-walk mode */}
      {isFromWalk && walkDetail && (
        <div className="course_create_date_row">
          <span className="course_create_date_text">{walkDate}</span>
          <button className="course_create_share_btn" onClick={() => void handleShare()} type="button">
            <svg viewBox="0 0 12 12" xmlns="http://www.w3.org/2000/svg">
              <path d="M6 1v7M6 1L3 4M6 1l3 3M2 8v2.5h8V8" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            </svg>
          </button>
        </div>
      )}

      {/* Map — rounded corners */}
      <div className="course_create_map_wrap">
        {mapLoadFailed ? (
          <div className="course_create_map_no_load">지도를 불러올 수 없습니다</div>
        ) : (
          <div ref={mapRef} className="course_create_map" />
        )}

        {!mapLoadFailed && waypoints.length === 0 && (
          <div className="course_create_map_hint">
            {isEditMode ? '경로가 초기화되었습니다. 지도를 탭하여 새 경로를 그려보세요' : '지도를 탭하여 경로를 그려보세요'}
          </div>
        )}

        {/* Expand icon overlay */}
        {!mapLoadFailed && (
          <button className="course_create_map_expand" type="button" aria-label="지도 확대" onClick={() => {
            // from-walk 모드: 전체화면 지도 페이지로 이동 (WalkDetailPage와 동일 UX)
            if (isFromWalk && walkId != null) {
              navigate(`/walk/map-expand/${walkId}`);
              return;
            }
            // 편집/신규 코스: 전체화면 페이지가 없으므로 fitBounds 폴백
            const map = mapInstanceRef.current;
            if (map && waypoints.length >= 2) {
              const bounds = new google.maps.LatLngBounds();
              for (const [lat, lng] of waypoints) bounds.extend({ lat, lng });
              map.fitBounds(bounds, { top: 30, right: 30, bottom: 30, left: 30 });
            }
          }}>
            <svg width="40" height="40" viewBox="16 8 40 40" fill="none">
              <rect x="16" y="8" width="40" height="40" rx="8" fill="white"/>
              <path d="M46 18H39M46 18V25M46 18L39 25M26 38H33M26 38V31M26 38L33 31" stroke="#614108" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
        )}
      </div>

      {/* Scrollable body */}
      <div className="course_create_body">
        {/* Stats bar — "총 N개 지점  스팟 N" + "스팟 추가" */}
        <div className="course_create_stats_bar">
          <div className="course_create_stats_text">
            <span>총 <strong>{waypoints.length}</strong>개 지점</span>
            <span>스팟 <strong>{spots.length}</strong></span>
          </div>
          <button
            className="course_create_add_spot_btn"
            onClick={handleAddSpot}
            type="button"
          >
            스팟 추가
          </button>
        </div>

        {/* Spot summary grid */}
        {spots.length > 0 && (
          <div className="course_create_spot_summary">
            {SPOT_TYPES.map(s => {
              const count = spots.filter(sp => sp.type === s.type).length;
              return (
                <div className="course_create_spot_summary_item" key={s.type}>
                  <div className="course_create_spot_summary_icon">
                    <SpotIcon type={s.type} size={32} />
                    <span className="course_create_spot_summary_badge">{count}</span>
                  </div>
                  <span className="course_create_spot_summary_label">{s.label}</span>
                </div>
              );
            })}
          </div>
        )}

        {/* 코스 정보 label */}
        <p className="course_create_section_label">코스 정보</p>

        {/* Data card — 2x2 grid (from-walk mode) */}
        {isFromWalk && walkDetail ? (
          <div className="course_create_data_card">
            <div className="course_create_data_row">
              <div className="course_create_data_item">
                <span className="course_create_data_label">거리(km)</span>
                <span className="course_create_data_value">{walkDetail.distance.toFixed(1)}</span>
              </div>
              <div className="course_create_data_item">
                <span className="course_create_data_label">시간(분)</span>
                <span className="course_create_data_value">{walkMinutes}</span>
              </div>
            </div>
            <div className="course_create_data_row">
              <div className="course_create_data_item">
                <span className="course_create_data_label">칼로리(Kcal)</span>
                <span className="course_create_data_value">{walkCalories.toFixed(3)}</span>
              </div>
              <div className="course_create_data_item">
                <span className="course_create_data_label">적립(Gold)</span>
                <span className="course_create_data_value">{walkGold}</span>
              </div>
            </div>
          </div>
        ) : (
          <div className="course_create_data_card">
            <div className="course_create_data_row">
              <div className="course_create_data_item">
                <span className="course_create_data_label">거리(km)</span>
                <span className="course_create_data_value">{distanceKm > 0 ? distanceKm.toFixed(2) : '-'}</span>
              </div>
              <div className="course_create_data_item">
                <span className="course_create_data_label">지점 수</span>
                <span className="course_create_data_value">{waypoints.length}개</span>
              </div>
            </div>
          </div>
        )}

        {/* Spots list */}
        {spots.length > 0 && (
          <div className="course_create_spots_section">
            <div className="course_create_spots_list">
              {spots.map(s => (
                <div
                  key={s.id}
                  className="course_create_spot_item"
                  onClick={() => {
                    setEditingSpot(s);
                    modalOpenRef.current = true;
                    setShowSpotModal(true);
                  }}
                >
                  <div className="course_create_spot_emoji">
                    <SpotIcon type={s.type} size={20} />
                  </div>
                  <div className="course_create_spot_info">
                    <div className="course_create_spot_name">
                      {s.name || spotLabel(s.type)}
                    </div>
                    <div className="course_create_spot_type">{spotLabel(s.type)}</div>
                  </div>
                  <button
                    className="course_create_spot_remove"
                    onClick={e => { e.stopPropagation(); handleRemoveSpot(s.id); }}
                    type="button"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Form */}
        <div className="course_create_form">
          {/* Title */}
          <div className="course_create_field">
            <label htmlFor="course_title">코스 이름</label>
            <input
              id="course_title"
              className="course_create_input"
              placeholder="예: 한강 난지도 산책로"
              value={title}
              onChange={e => setTitle(e.target.value)}
              onFocus={handleInputFocus}
              maxLength={100}
            />
          </div>

          {/* Description */}
          <div className="course_create_field">
            <label htmlFor="course_desc">설명</label>
            <div className="course_create_desc_wrap">
              <textarea
                id="course_desc"
                className="course_create_input textarea"
                placeholder="코스에 대한 설명을 적어주세요"
                value={desc}
                onChange={e => setDesc(e.target.value)}
                onFocus={handleInputFocus}
                rows={3}
                maxLength={500}
              />
              <div className="course_create_desc_counter">{desc.length}/500자</div>
            </div>
          </div>

          {/* Difficulty */}
          <div className="course_create_field">
            <label>난이도</label>
            <div className="course_create_difficulty_chips">
              {([['EASY', '초급'], ['MODERATE', '중급'], ['HARD', '상급']] as const).map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  className={`course_create_difficulty_chip${difficulty === value ? ' selected' : ''}`}
                  onClick={() => setDifficulty(value)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          {/* Estimated minutes */}
          <div className="course_create_field">
            <label htmlFor="course_minutes">예상 시간(분)</label>
            <input
              id="course_minutes"
              className="course_create_input"
              type="number"
              inputMode="numeric"
              placeholder="38"
              min={1}
              max={9999}
              value={estimatedMinutes}
              onChange={e => setEstimatedMinutes(e.target.value)}
              onFocus={handleInputFocus}
            />
          </div>
        </div>

      </div>

      {/* Bottom buttons — dual half buttons with shadow */}
      <div className="course_create_submit_wrap">
        <button
          className="course_create_btn_reset"
          onClick={handleResetAll}
          type="button"
        >
          초기화
        </button>
        <button
          className="course_create_btn_submit"
          onClick={handleSubmit}
          disabled={activeMutation.isPending}
          type="button"
        >
          {activeMutation.isPending
            ? (isEditMode ? '수정 중...' : '등록 중...')
            : (isEditMode ? '코스 수정' : '산책 코스 등록하기')}
        </button>
      </div>
      {activeMutation.isError && (
        <p className="course_create_error_text">
          {(activeMutation.error as Error & { response?: { data?: { message?: string } } })?.response?.data?.message
            ?? (isEditMode ? '수정에 실패했습니다. 다시 시도해주세요.' : '등록에 실패했습니다. 다시 시도해주세요.')}
        </p>
      )}

      {/* Spot modal */}
      {showSpotModal && (
        <SpotModal
          key={editingSpot?.id ?? 'new'}
          onConfirm={handleSpotConfirm}
          onClose={closeModal}
          editSpot={editingSpot ?? undefined}
          spots={spots}
        />
      )}
    </div>
  );
}

export default CourseCreatePage;
