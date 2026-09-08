import { useQuery } from '@tanstack/react-query';
import { userPublicProfileService } from '../../services/userPublicProfileService';

export function useUserPublicProfile(userId: number) {
  return useQuery({
    queryKey: ['users', userId, 'publicProfile'],
    queryFn: () => userPublicProfileService.getPublicProfile(userId),
    staleTime: 1000 * 60, // 60s
    retry: (failureCount, error) => {
      // 404 (withdrawn / blocked_me) — no retry
      const status = (error as { response?: { status?: number } }).response?.status;
      if (status === 404) return false;
      return failureCount < 2;
    },
  });
}
