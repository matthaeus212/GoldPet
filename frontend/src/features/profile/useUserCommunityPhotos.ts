import { useInfiniteQuery } from '@tanstack/react-query';
import { userPublicProfileService } from '../../services/userPublicProfileService';

export function useUserCommunityPhotos(userId: number) {
  return useInfiniteQuery({
    queryKey: ['users', userId, 'communityPhotos'],
    queryFn: ({ pageParam }) =>
      userPublicProfileService.getCommunityPhotos(userId, pageParam as string | undefined),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    staleTime: 1000 * 60, // 60s
  });
}
