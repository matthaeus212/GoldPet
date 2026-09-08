import { useState, useCallback, useMemo } from 'react';
import type { WalkSpot } from '../../../services/walkService';

interface UseWalkPhotoGalleryReturn {
    /** All PHOTO spots filtered from the walk */
    photoSpots: WalkSpot[];
    /** Resolved HTTP URLs for gallery (aligned with photoSpots order). */
    resolvedUrls: string[];
    /** Whether gallery photos are being resolved. Always false after T1-3 (synchronous). */
    isLoading: boolean;
    /** Whether gallery is open */
    galleryOpen: boolean;
    /** Index of the initially displayed photo in the gallery */
    initialIndex: number;
    /** Single resolved URL for bottom sheet preview (for the selected spot) */
    previewUrl: string | null;
    /** Whether preview photo is loading. Always false after T1-3. */
    previewLoading: boolean;
    /** Whether preview photo failed to load */
    previewError: boolean;
    /** Open gallery showing all photos, starting from the given spot */
    openGallery: (spot: WalkSpot) => void;
    /** Close gallery */
    closeGallery: () => void;
    /** Resolve a single photo for bottom sheet preview */
    resolvePreview: (spot: WalkSpot) => void;
    /** Clear preview state (when closing bottom sheet) */
    clearPreview: () => void;
}

/**
 * T1-3 — imageUrl 은 항상 HTTPS presigned URL (WalkService 에서 mint). 구
 * `/files/{key}/content` proxy 경로는 dead path 이며 T1-4 에서 410 Gone 으로
 * 전환 예정. 해당 분기가 실제 히트할 일이 없으므로 blob 생성/revoke/ref
 * 추적 코드를 제거하고 동기 URL 선택으로 단순화.
 *
 * 방어적으로 non-HTTP 경로가 들어오면 빈 문자열을 반환해 DOM 에서
 * 조용히 skip — 과거 blob 경로처럼 API 호출로 프록시를 열지 않는다.
 */
function resolvePhotoUrl(imageUrl: string | null | undefined): string {
    if (!imageUrl) return '';
    if (imageUrl.startsWith('http')) return imageUrl;
    return '';
}

export function useWalkPhotoGallery(spots: WalkSpot[] | undefined): UseWalkPhotoGalleryReturn {
    const [galleryOpen, setGalleryOpen] = useState(false);
    const [initialIndex, setInitialIndex] = useState(0);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [previewError, setPreviewError] = useState(false);

    const photoSpots = useMemo(
        () => (spots ?? []).filter(
            (s): s is WalkSpot & { imageUrl: string } => s.type === 'PHOTO' && !!s.imageUrl,
        ),
        [spots],
    );

    // Derived synchronously — viewer variant preferred, fallback to original.
    const resolvedUrls = useMemo(
        () => photoSpots.map((s) => resolvePhotoUrl(s.imageUrlViewer ?? s.imageUrl)),
        [photoSpots],
    );

    const openGallery = useCallback((spot: WalkSpot) => {
        // 일치 우선순위: spot.id → imageUrl (timestamp은 Date 재생성 시 레퍼런스 불일치 가능)
        const idx = photoSpots.findIndex((s) => {
            if (spot.id != null && s.id != null) return s.id === spot.id;
            return s.imageUrl === spot.imageUrl;
        });
        setInitialIndex(idx >= 0 ? idx : 0);
        setGalleryOpen(true);
    }, [photoSpots]);

    const closeGallery = useCallback(() => {
        setGalleryOpen(false);
    }, []);

    const resolvePreview = useCallback((spot: WalkSpot) => {
        if (spot.type !== 'PHOTO' || !spot.imageUrl) return;
        const resolved = resolvePhotoUrl(spot.imageUrlViewer ?? spot.imageUrl);
        if (resolved.length === 0) {
            setPreviewUrl(null);
            setPreviewError(true);
            return;
        }
        setPreviewUrl(resolved);
        setPreviewError(false);
    }, []);

    const clearPreview = useCallback(() => {
        setPreviewUrl(null);
        setPreviewError(false);
    }, []);

    return {
        photoSpots,
        resolvedUrls,
        isLoading: false,
        galleryOpen,
        initialIndex,
        previewUrl,
        previewLoading: false,
        previewError,
        openGallery,
        closeGallery,
        resolvePreview,
        clearPreview,
    };
}
