import { useMemo, useCallback } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { walkService } from '../../../services/walkService';
import type { PageResponse, WalkPhotoItem } from '../../../services/walkService';

const PAGE_SIZE = 20;
// 서버 서명 캐시 창(20분)보다 짧게 — 재요청 시 항상 잔여 유효시간이 넉넉한 URL 을 받는다.
export const FEED_STALE_MS = 10 * 60 * 1000;

export type WalkPhotoFeedMode = 'owned' | 'shared';

export function useWalkPhotoFeed({ mode }: { mode: WalkPhotoFeedMode }) {
  const queryKey = mode === 'owned'
    ? ['walk', 'my', 'photos'] as const
    : ['walk', 'public', 'photos'] as const;

  const q = useInfiniteQuery<PageResponse<WalkPhotoItem>, Error>({
    queryKey: queryKey as unknown as readonly unknown[],
    queryFn: ({ pageParam = 0 }) =>
      mode === 'owned'
        ? walkService.getMyPhotos({ page: pageParam as number, size: PAGE_SIZE })
        : walkService.getPublicPhotos({ page: pageParam as number, size: PAGE_SIZE }),
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
    initialPageParam: 0,
    // 사진 URL 은 presigned 라 응답마다 서명이 달라진다. staleTime 0(기본)이면 마운트/포커스마다
    // 전 페이지를 refetch 하고, 그때마다 모든 <img src> 가 바뀌어 브라우저가 전 사진을 재다운로드·
    // 재디코딩한다(iOS WKWebView 메모리 급증 → 렌더러 강제종료). 서버 서명 캐시(20분)와 짝을 맞춰
    // 불필요한 refetch 자체를 없앤다.
    staleTime: FEED_STALE_MS,
    refetchOnWindowFocus: false,
  });

  const photos: WalkPhotoItem[] = useMemo(
    () => q.data?.pages.flatMap(p => p.content) ?? [],
    [q.data],
  );
  const findIndexBySpotId = useCallback(
    (spotId: number, walkId: number) => photos.findIndex(p => p.id === spotId && p.walkId === walkId),
    [photos],
  );

  return { ...q, photos, queryKey, findIndexBySpotId, pageSize: PAGE_SIZE };
}
