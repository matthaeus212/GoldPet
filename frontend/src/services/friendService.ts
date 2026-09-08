// Friend Service - API integration for Friend features

import apiClient from './api/client';
import { typedClient } from './api/typedClient';
import type { FriendResponse, SpeciesResponse } from '../types/api';

// Generated aliases (Phase 3.10 + follow-up). Backend `petGender`/`status` are
// plain strings — narrow at the consumption site if needed.
export type Friend = FriendResponse;
// Backend SpeciesResponse extended with description + breedCount (follow-up).
export type Species = SpeciesResponse;

export const friendService = {
  getFriends: async (filterJson: string, page = 0, size = 20): Promise<{ content: Friend[], last: boolean }> => {
    try {
      const filters = JSON.parse(filterJson);
      const params: Record<string, string | number | string[]> = { page, size };

      if (filters.distance) params.distance = filters.distance;
      if (filters.sortOrder) params.sort = filters.sortOrder;
      if (filters.petTypes?.length) params.petTypes = filters.petTypes.join(',');
      if (filters.genders?.length) params.genders = filters.genders.join(',');
      if (filters.lat) params.lat = filters.lat;
      if (filters.lng) params.lng = filters.lng;

      const response = await typedClient.get('/api/v1/friends', { params });
      return response.data as { content: Friend[]; last: boolean };
    } catch (error: unknown) {
      console.error('Failed to fetch friends:', error);
      throw error;
    }
  },

  getNearbyFriends: async (lat: number, lng: number, radiusMeters = 5000): Promise<Friend[]> => {
    try {
      const response = await typedClient.get('/api/v1/users/search/nearby', {
        params: { lat, lon: lng, radius: radiusMeters },
      });
      return response.data as unknown as Friend[];
    } catch (error: unknown) {
      console.error('Failed to fetch nearby friends:', error);
      throw error;
    }
  },

  searchFriends: async (query: string, limit = 10): Promise<Friend[]> => {
    try {
      const response = await typedClient.get('/api/v1/users/search', {
        params: { query, limit },
      });
      return response.data as unknown as Friend[];
    } catch (error: unknown) {
      console.error('Failed to search friends:', error);
      throw error;
    }
  },

  sendFriendRequest: async (userId: number): Promise<{ success: boolean }> => {
    try {
      // POST /likes is legacy and not in the OpenAPI spec; keep on apiClient until
      // backend removes it or exposes it via Springdoc.
      await apiClient.post(`/likes`, { toUserId: userId });
      return { success: true };
    } catch {
      throw new Error('친구 요청에 실패했습니다.');
    }
  },

  // TODO: 백엔드 API 구현 필요 - 수락은 mutual like(MatchController)로 자동 처리됨
  acceptFriendRequest: async (requestId: number): Promise<{ success: boolean }> => {
    void requestId;
    throw new Error('acceptFriendRequest: 백엔드 API가 구현되지 않았습니다. 상호 좋아요 시 자동 매칭됩니다.');
  },

  // TODO: 백엔드 API 구현 필요
  rejectFriendRequest: async (requestId: number): Promise<{ success: boolean }> => {
    void requestId;
    throw new Error('rejectFriendRequest: 백엔드 API가 구현되지 않았습니다.');
  },

  // TODO: 백엔드 API 구현 필요
  removeFriend: async (friendId: number): Promise<{ success: boolean }> => {
    void friendId;
    throw new Error('removeFriend: 백엔드 API가 구현되지 않았습니다.');
  },

  getSpecies: async (): Promise<Species[]> => {
    try {
      const response = await typedClient.get('/api/v1/pets/species');
      return response.data as Species[];
    } catch (error: unknown) {
      console.error('Failed to fetch species:', error);
      throw error;
    }
  },

  // `source` = originating sort/list (compatible|distance|popular|received|home …). Sent as a query
  // param so it reaches the backend like_events log WITHOUT requiring an OpenAPI schema regen
  // (the endpoint declares no request body). Omitted when not provided.
  likeUser: async (
    targetUserId: number,
    source?: string,
  ): Promise<{ likeId: number; toUserId: number; isMutual: boolean }> => {
    try {
      const response = await typedClient.postPath(
        '/api/v1/friends/{targetUserId}/like',
        { targetUserId },
        undefined,
        source ? { params: { source } } : undefined,
      );
      return response.data as { likeId: number; toUserId: number; isMutual: boolean };
    } catch (error: unknown) {
      console.error('Failed to like user:', error);
      throw error;
    }
  },

  // `source` mirrors likeUser — sent as a query param so the CANCEL like_events row also records
  // its originating list/sort (symmetry with LIKE). Omitted when not provided.
  unlikeUser: async (targetUserId: number, source?: string): Promise<{ success: boolean }> => {
    try {
      const response = await typedClient.deletePath(
        '/api/v1/friends/{targetUserId}/like',
        { targetUserId },
        source ? { params: { source } } : undefined,
      );
      return response.data as unknown as { success: boolean };
    } catch (error: unknown) {
      console.error('Failed to unlike user:', error);
      throw error;
    }
  },

  rejectLike: async (senderUserId: number): Promise<{ success: boolean }> => {
    try {
      await typedClient.deletePath(
        '/api/v1/friends/likes/received/{senderId}',
        { senderId: senderUserId },
      );
      return { success: true };
    } catch (error: unknown) {
      console.error('Failed to reject like:', error);
      throw error;
    }
  },

  getMyLikes: async (): Promise<Friend[]> => {
    try {
      const response = await typedClient.get('/api/v1/friends/likes/my');
      return response.data as Friend[];
    } catch (error: unknown) {
      console.error('Failed to get my likes:', error);
      throw error;
    }
  },

  getMutualLikes: async (): Promise<Friend[]> => {
    try {
      const response = await typedClient.get('/api/v1/friends/likes/mutual');
      return response.data as Friend[];
    } catch (error: unknown) {
      console.error('Failed to get mutual likes:', error);
      throw error;
    }
  },

  getReceivedLikes: async (): Promise<Friend[]> => {
    try {
      const response = await typedClient.get('/api/v1/friends/likes/received');
      return response.data as Friend[];
    } catch (error: unknown) {
      console.error('Failed to get received likes:', error);
      throw error;
    }
  },

  checkLikeStatus: async (targetUserId: number): Promise<boolean> => {
    try {
      const response = await typedClient.getPath(
        '/api/v1/friends/{targetUserId}/like/status',
        { targetUserId },
      );
      return (response.data as { hasLiked: boolean }).hasLiked;
    } catch {
      return false;
    }
  },
};

export const PET_TAG_MAPPINGS: Record<string, string> = {
    // Activity
    'Activity:ENERGIZER': '에너자이저',
    'Activity:ACTIVE': '활발함',
    'Activity:NORMAL': '보통 활동량',
    'Activity:CALM': '얌전함',
    'Activity:STATIC': '매우 정적',

    // Friendliness
    'Friendliness:SOCIAL': '모두의 친구',
    'Friendliness:PEOPLE_ONLY': '사람 좋아함',
    'Friendliness:SELECTIVE': '선택적 친화',
    'Friendliness:SHY': '낯가림',
    'Friendliness:INDEPENDENT': '독립적',

    // Training
    'Training:PROFESSIONAL': '전문 교육 수료',
    'Training:BASIC': '기본 매너',
    'Training:TRAINING': '교육 중',
    'Training:FREE_SPIRIT': '자유로운 영혼',

    // Barking
    'Barking:VOCAL': '자주 짖음',
    'Barking:NECESSARY': '가끔 짖음',
    'Barking:QUIET': '조용함',

    // Toy
    'Toy:MANIA': '장난감 마니아',
    'Toy:LIKE': '장난감 좋아함',
    'Toy:NORMAL': '놀이 보통',
    'Toy:DISLIKE': '장난감 무관심',

    // Walk
    'Walk:ADDICTED': '산책 중독',
    'Walk:ENJOY': '산책 즐김',
    'Walk:MODERATE': '적당한 산책',
    'Walk:INDOOR': '실내파',

    // Petting
    'Petting:LOVER': '스킨십 중독',
    'Petting:LIKE': '스킨십 좋아함',
    'Petting:ACCEPT': '스킨십 허용',
    'Petting:PRIVATE': '터치 싫어함',

    // Friendship
    'Friendship:SOCIAL': '적극적 친구',
    'Friendship:MANNER': '매너 좋은',
    'Friendship:CAREFUL': '조심성 많음',
    'Friendship:LONER': '혼자가 편함',

    // Allergy (Only showing YES for consistency)
    'AllergyMeat:YES': '육류 알러지',
    'AllergyDairyEgg:YES': '유제품/알 알러지',
    'AllergyGrain:YES': '곡물 알러지',
    'AllergyOther:YES': '기타 알러지',
};

export const formatPetTag = (tag: string): string => {
    // Check direct match
    if (PET_TAG_MAPPINGS[tag]) return PET_TAG_MAPPINGS[tag];

    // Handle Note
    if (tag.startsWith('Note:')) return tag.substring(5);

    // Hide NO/UNKNOWN system tags
    if (tag.includes(':NO') || tag.includes(':UNKNOWN')) return '';

    // Fallback for legacy simple tags or custom tags
    return tag;
};
