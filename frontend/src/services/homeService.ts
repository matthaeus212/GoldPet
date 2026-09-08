// Home Service - API integration for Home features

import { typedClient } from './api/typedClient';
import type { HomeResponse, HomeTodayWalkResponse, UnreadCountResponse } from '../types/api';
import { truncateLocation, formatDistanceKm } from '../utils/formatters';

export interface HomeRecommendation {
  id: number;
  name: string;
  petAge: number;
  petBreed: string;
  description: string;
  /** 위치 없는 후보(원시 거리<=0)는 null — "0 이내" 오표시 방지 가드. */
  distance: string | null;
  images: string[];
  imagesThumbnail?: string[];
  imagesViewer?: string[];
  /** T2-D2: WebP variant URLs (backend D1 공급 시 자동 활용) */
  imagesThumbnailWebp?: (string | null)[];
  imagesViewerWebp?: (string | null)[];
  isNeutered: boolean;
  gender: 'MALE' | 'FEMALE';
  nickname: string;
  profileImageUrl?: string;
  profileImageUrlThumbnail?: string;
  profileImageUrlViewer?: string;
  profileImages: string[];  // Owner's all profile images
  profileImagesThumbnail?: string[];
  profileImagesViewer?: string[];
  /** T2-D2: WebP variant URLs (backend D1 공급 시 자동 활용) */
  profileImagesThumbnailWebp?: (string | null)[];
  ownerGender?: string;
  ownerAge?: number;
  ownerIntro?: string;
  ownerMbti?: string;
  ownerInterests: string[];
  ownerHobbies: string[];
  locationText?: string;
  tags: string[];
}

export interface HomeRecentWalk {
  id: number;
  distanceKm: number;
  durationSeconds: number;
  endTime: string;
  startAddress: string | null;
  petNames: string[];
  petProfileImageUrls: string[];
  petProfileImageUrlsThumbnail?: string[];
  petProfileImageUrlsViewer?: string[];
}

/** 오늘(자정 기준) 누적 산책 통계 + 대표 반려동물 정보. */
export type HomeTodayWalk = HomeTodayWalkResponse;

export interface HomeDataResponse {
  recommendations: HomeRecommendation[];
  recentWalk: HomeRecentWalk | null;
  todayWalk: HomeTodayWalk;
  notifications: {
    hasUnread: boolean;
    count: number;
  };
  myPetNames: string[];
}

const emptyTodayWalk: HomeTodayWalk = {
  distanceKm: 0,
  durationMinutes: 0,
  caloriesBurned: 0,
  earnedGold: 0,
  walkCount: 0,
};

const emptyHomeData: HomeDataResponse = {
  recommendations: [],
  recentWalk: null,
  todayWalk: emptyTodayWalk,
  notifications: { hasUnread: false, count: 0 },
  myPetNames: [],
};


export const homeService = {
  getHomeData: async (): Promise<HomeDataResponse> => {
    try {
      const response = await typedClient.get('/api/v1/home');
      const data = response.data as HomeResponse;

      const recommendations: HomeRecommendation[] = (data.recommendations ?? []).map((r) => {
        const hasImages = r.images?.length > 0;
        return {
          id: r.id,
          name: r.petName,
          petAge: r.petAge,
          petBreed: r.petBreed,
          description: r.description || '',
          distance: r.distance > 0 ? formatDistanceKm(r.distance) : null,
          images: hasImages ? r.images : ['/assets/images/common/pet_none_img02.svg'],
          imagesThumbnail: hasImages ? r.imagesThumbnail : undefined,
          imagesViewer: hasImages ? r.imagesViewer : undefined,
          isNeutered: r.isNeutered,
          gender: r.petGender as 'MALE' | 'FEMALE',
          nickname: r.nickname,
          profileImageUrl: r.profileImageUrl,
          profileImageUrlThumbnail: r.profileImageUrlThumbnail,
          profileImageUrlViewer: r.profileImageUrlViewer,
          profileImages: r.profileImages ?? [],
          profileImagesThumbnail: r.profileImagesThumbnail,
          profileImagesViewer: r.profileImagesViewer,
          ownerGender: r.ownerGender,
          ownerAge: r.ownerAge,
          ownerIntro: r.ownerIntro,
          ownerMbti: r.ownerMbti,
          ownerInterests: r.ownerInterests ?? [],
          ownerHobbies: r.ownerHobbies ?? [],
          locationText: truncateLocation(r.locationText),
          tags: r.tags ?? [],
        };
      });

      return {
        recommendations,
        recentWalk: data.recentWalk ? {
          id: data.recentWalk.id,
          distanceKm: data.recentWalk.distanceKm,
          durationSeconds: data.recentWalk.durationSeconds,
          endTime: data.recentWalk.endTime,
          startAddress: data.recentWalk.startAddress ?? null,
          petNames: data.recentWalk.petNames ?? [],
          petProfileImageUrls: data.recentWalk.petProfileImageUrls ?? [],
          petProfileImageUrlsThumbnail: data.recentWalk.petProfileImageUrlsThumbnail,
          petProfileImageUrlsViewer: data.recentWalk.petProfileImageUrlsViewer,
        } : null,
        todayWalk: data.todayWalk ?? emptyTodayWalk,
        notifications: data.notifications ?? { hasUnread: false, count: 0 },
        myPetNames: data.myPetNames ?? [],
      };
    } catch {
      return emptyHomeData;
    }
  },

  getNotifications: async (): Promise<{ hasUnread: boolean; count: number }> => {
    try {
      const response = await typedClient.get('/api/v1/notifications/unread-count');
      const data = response.data as UnreadCountResponse;
      return { hasUnread: data.count > 0, count: data.count };
    } catch {
      return { hasUnread: false, count: 0 };
    }
  },
};
