import { useEffect, useLayoutEffect, useState } from 'react';
import { walkService } from '../../../services/walkService';
import type { WalkPhotoItem, WalkSession, WalkSpot } from '../../../services/walkService';
import { useWalkPhotoFeed, type WalkPhotoFeedMode } from './useWalkPhotoFeed';

type UseDeepLinkPhotoParams = {
  walkId: number;
  spotId: number;
  mode: WalkPhotoFeedMode;
  photoFromState?: WalkPhotoItem | null;
  deepLinkTimeoutMs?: number;
};

type UseDeepLinkPhotoResult = {
  initialPhoto: WalkPhotoItem | null;
  photos: WalkPhotoItem[];
  initialIndex: number;
  allowSwipe: boolean;
  empty: boolean;
  loading: boolean;
  fetchNextPage: () => void;
  hasNextPage: boolean;
};

const MAX_TRACKB_PREFETCH_PAGES = 5;
const PAGE_SIZE = 20;

function isSlowNetwork(): boolean {
  if (typeof navigator === 'undefined') return false;
  const nav = navigator as Navigator & {
    connection?: { effectiveType?: string; saveData?: boolean };
  };
  const eff = nav.connection?.effectiveType;
  return eff === '2g' || eff === 'slow-2g' || nav.connection?.saveData === true;
}

function toPhotoItem(walk: WalkSession, spot: WalkSpot): WalkPhotoItem {
  return {
    id: spot.id ?? 0,
    imageUrl: spot.imageUrl,
    imageUrlViewer: spot.imageUrlViewer,
    imageUrlMedium: spot.imageUrlMedium,
    imageUrlThumb: spot.imageUrlThumb,
    note: spot.note,
    walkDate: walk.startTime,
    walkId: walk.id,
    userId: walk.userId ?? 0,
    petName: walk.petNames?.[0],
    petImageUrl: walk.petProfileImageUrls?.[0] ?? undefined,
    hiddenFromPublic: spot.hiddenFromPublic ?? false,
  };
}

export function useDeepLinkPhoto({
  walkId,
  spotId,
  mode,
  photoFromState,
  deepLinkTimeoutMs,
}: UseDeepLinkPhotoParams): UseDeepLinkPhotoResult {
  const [initialPhoto, setInitialPhoto] = useState<WalkPhotoItem | null>(photoFromState ?? null);
  const [photos, setPhotos] = useState<WalkPhotoItem[]>(
    photoFromState ? [photoFromState] : []
  );
  // 초기 [photoFromState] 한 장 상태에서도 스와이프 시도해봤자 no-op이므로 true로 시작.
  // R2 race guard는 hydration 완료 후 slideTo로 처리.
  const [allowSwipe, setAllowSwipe] = useState(true);
  const [empty, setEmpty] = useState(false);
  const [trackALoaded, setTrackALoaded] = useState<boolean>(!!photoFromState);

  const effectiveTimeoutMs = deepLinkTimeoutMs ?? (isSlowNetwork() ? 10_000 : 5_000);

  const {
    photos: photosFromFeed,
    fetchNextPage,
    hasNextPage: rqHasNextPage,
    isFetchingNextPage,
  } = useWalkPhotoFeed({ mode });

  const hasNextPage = !!rqHasNextPage;

  // trackA — 단건 즉시 확보 (이미 photoFromState 있으면 skip)
  useEffect(() => {
    if (initialPhoto || !walkId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      if (initialPhoto) setTrackALoaded(true);
      return;
    }
    let cancelled = false;
    walkService
      .getWalkDetail(Number(walkId))
      .then((walk) => {
        if (cancelled) return;
        const target =
          walk.spots?.find((s) => s.id === Number(spotId)) ??
          walk.spots?.find((s) => s.type === 'PHOTO');
        if (target?.imageUrl) {
          const photo = toPhotoItem(walk, target);
          setInitialPhoto(photo);
          setPhotos((prev) => (prev.length === 0 ? [photo] : prev));
          setTrackALoaded(true);
        } else {
          setEmpty(true);
        }
      })
      .catch(() => {
        // timeout guard will handle
      });
    return () => {
      cancelled = true;
    };
  }, [walkId, spotId, initialPhoto]);

  // trackA 렌더 구간 — background prefetch (빈 피드인 경우만 첫 페이지)
  useEffect(() => {
    if (photosFromFeed.length === 0 && rqHasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [photosFromFeed.length, rqHasNextPage, isFetchingNextPage, fetchNextPage]);

  // trackB — 캐시 hydration (R2 race guard)
  // 주의: Swiper에게 slideTo를 직접 호출하지 않음. Swiper는 Viewer에서 initialSlide={initialIndex} + key remount로 위치 지정.
  // 여기서는 photos state만 업데이트하고, Viewer가 올바른 initialSlide로 Swiper를 재생성.
  useLayoutEffect(() => {
    if (photosFromFeed.length === 0) return;
    const idx = photosFromFeed.findIndex(
      (p) => p.id === Number(spotId) && p.walkId === Number(walkId)
    );
    if (idx < 0) {
      // 아직 캐시에 없음 → 최대 5페이지까지만 background prefetch (블로킹 금지)
      if (
        rqHasNextPage &&
        !isFetchingNextPage &&
        photosFromFeed.length < MAX_TRACKB_PREFETCH_PAGES * PAGE_SIZE
      ) {
        fetchNextPage();
      }
      return;
    }

    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPhotos((prev) => (prev === photosFromFeed ? prev : photosFromFeed));
    setAllowSwipe((prev) => (prev ? prev : true));
  }, [photosFromFeed, walkId, spotId, rqHasNextPage, isFetchingNextPage, fetchNextPage]);

  // photos 배열 안에서 현재 spot의 위치 (Viewer가 Swiper initialSlide로 사용)
  const initialIndex = (() => {
    if (photos.length === 0) return 0;
    const idx = photos.findIndex(
      (p) => p.id === Number(spotId) && p.walkId === Number(walkId)
    );
    return idx >= 0 ? idx : 0;
  })();

  // Timeout guard — trackA도 없고 empty 아니면 타임아웃 후 empty 처리
  useEffect(() => {
    if (initialPhoto || empty) return;
    const t = setTimeout(() => {
      setEmpty((prev) => (prev ? prev : true));
    }, effectiveTimeoutMs);
    return () => clearTimeout(t);
  }, [initialPhoto, empty, effectiveTimeoutMs]);

  const loading = !initialPhoto && !empty && !trackALoaded;

  return {
    initialPhoto,
    photos,
    initialIndex,
    allowSwipe,
    empty,
    loading,
    fetchNextPage,
    hasNextPage,
  };
}
