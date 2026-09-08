import { useQuery } from '@tanstack/react-query';
import type { PageResponse } from '../types/api';

/**
 * Query key convention:
 * - List queries: [resource, 'list', params]
 * - Detail queries: [resource, 'detail', id]
 *
 * Cache invalidation: mutations use queryClient.invalidateQueries({ queryKey: [resource] })
 * to invalidate both list and detail caches.
 */

interface UsePagedQueryOptions<T> {
  queryKey: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  fetchFn: (params: { page: number; size: number; [key: string]: any }) => Promise<PageResponse<T>>;
  page: number;
  size: number;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  filters?: Record<string, any>;
  enabled?: boolean;
}

export function usePagedQuery<T>({ queryKey, fetchFn, page, size, filters = {}, enabled = true }: UsePagedQueryOptions<T>) {
  return useQuery({
    queryKey: [queryKey, 'list', { page, size, ...filters }],
    queryFn: () => fetchFn({ page, size, ...filters }),
    enabled,
    placeholderData: (prev) => prev,
  });
}
