import { useInfiniteQuery } from '@tanstack/react-query';
import { userPublicProfileService } from '../../services/userPublicProfileService';

/**
 * community-author-profile-gallery Phase 2 F1 — `/api/v1/users/{userId}/walks/photos` cursor 쿼리.
 *
 * `enabled` 프롭으로 "산책" 탭 진입 시에만 fetch (lazy) — 다른 탭에서 불필요한 네트워크 억제.
 */
export function useUserWalkPhotos(userId: number, enabled = true) {
  return useInfiniteQuery({
    queryKey: ['users', userId, 'walkPhotos'],
    queryFn: ({ pageParam }) =>
      userPublicProfileService.getWalkPhotos(userId, pageParam as string | undefined),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    staleTime: 1000 * 60,
    enabled,
  });
}
