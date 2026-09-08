import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 1000 * 30, // 30 seconds default - data considered fresh for 30s
      gcTime: 1000 * 60 * 5, // 5 minutes - unused data stays in cache for 5m
    },
    mutations: {
      onError: (error: unknown) => {
        console.error('Mutation error:', error);
      },
    },
  },
});
