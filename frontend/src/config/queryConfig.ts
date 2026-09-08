/**
 * React Query 캐싱 전략 설정
 *
 * 데이터 특성에 따른 staleTime 가이드:
 * - STATIC: 거의 변하지 않는 데이터 (종, 품종, 카테고리 등)
 * - SEMI_STATIC: 드물게 변하는 데이터 (설정, 관심사/취미 목록)
 * - DYNAMIC: 자주 변하는 데이터 (프로필, 게시글 목록)
 * - REAL_TIME: 실시간성이 중요한 데이터 (채팅, 알림)
 * - FRESH: 항상 최신 데이터가 필요한 경우
 */

export const CACHE_TIME = {
  // Static data - rarely or never changes
  STATIC: {
    staleTime: Infinity,
    gcTime: 1000 * 60 * 60 * 24, // 24 hours
  },

  // Semi-static data - changes occasionally (settings, lookup tables)
  SEMI_STATIC: {
    staleTime: 1000 * 60 * 60, // 1 hour
    gcTime: 1000 * 60 * 60 * 24, // 24 hours
  },

  // Dynamic data - changes frequently but can tolerate some staleness
  DYNAMIC: {
    staleTime: 1000 * 60, // 1 minute
    gcTime: 1000 * 60 * 5, // 5 minutes
  },

  // Real-time data - needs to be relatively fresh
  REAL_TIME: {
    staleTime: 1000 * 10, // 10 seconds
    gcTime: 1000 * 60 * 2, // 2 minutes
  },

  // Fresh data - always refetch (use sparingly, prefer invalidation)
  FRESH: {
    staleTime: 0,
    gcTime: 1000 * 60 * 5, // 5 minutes
  },
} as const;

/**
 * Query Key별 권장 캐싱 전략:
 *
 * STATIC:
 * - ['pets', 'species'] - 종 목록
 * - ['pets', 'breeds', speciesId] - 품종 목록
 * - ['community', 'categories'] - 카테고리 목록
 *
 * SEMI_STATIC:
 * - ['system', 'publicSettings'] - 시스템 설정
 * - ['pets', 'attributes'] - 펫 속성 스키마
 * - ['users', 'interests'] - 관심사 목록
 * - ['users', 'hobbies'] - 취미 목록
 *
 * DYNAMIC:
 * - ['users', 'me'] - 내 프로필 (mutation 후 invalidate)
 * - ['pets', 'my'] - 내 펫 목록 (mutation 후 invalidate)
 * - ['community', 'posts', ...] - 게시글 목록
 * - ['friends', 'likes', ...] - 친구 목록
 * - ['homeData'] - 홈 데이터
 *
 * REAL_TIME:
 * - ['chatRooms'] - 채팅방 목록
 * - ['chatMessages', chatId] - 채팅 메시지 (WebSocket 보완)
 * - ['notifications'] - 알림
 */
