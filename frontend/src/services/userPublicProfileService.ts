import apiClient from './api/client';

export type PublicUserStatus = 'NORMAL' | 'BLOCKED_BY_ME' | 'WITHDRAWN';

export interface PetProfileResponse {
  id: number;
  ownerId: number;
  name: string;
  species: string;
  breed: string | null;
  profileImageUrl: string | null;
  profileImageUrls: string[];
  profileImageUrlViewer: string | null;
  profileImageUrlThumbnail: string | null;
  profileImageUrlsViewer: string[] | null;
  profileImageUrlsThumbnail: string[] | null;
}

export interface PublicUserProfileResponse {
  userId: number;
  nickname: string | null;
  intro: string | null;
  profileUrl: string | null;
  profileUrlThumbnail: string | null;
  profileUrlViewer: string | null;
  profileUrls: string[];
  profileUrlsThumbnail: string[];
  profileUrlsViewer: string[];
  publicPostCount: number;
  status: PublicUserStatus;
  isMe: boolean;
  blockedByMe: boolean;
  pets: PetProfileResponse[];
}

export interface CommunityPhotoPost {
  id: number;
  createdAt: string;
  thumbnailUrl: string | null;
  mediumUrl: string | null;
  viewerUrl: string | null;
  imageCount: number;
}

export interface UserCommunityPhotosResponse {
  posts: CommunityPhotoPost[];
  nextCursor: string | null;
}

/**
 * community-author-profile-gallery Phase 2 F1 — `WalkPhotoResponse` (backend) 와 동일 shape.
 * Grid 썸네일 + ImageGalleryModal viewer URL 을 위한 최소 필드 + 보조 메타데이터.
 */
export interface UserWalkPhotoItem {
  id: number;
  imageUrl: string | null;
  imageUrlViewer: string | null;
  imageUrlThumb: string | null;
  note: string | null;
  walkDate: string;
  walkId: number;
  userId: number;
  petName: string | null;
  petImageUrl: string | null;
  hiddenFromPublic: boolean;
}

export interface UserWalkPhotosResponse {
  photos: UserWalkPhotoItem[];
  nextCursor: string | null;
}

export const userPublicProfileService = {
  getPublicProfile: async (userId: number): Promise<PublicUserProfileResponse> => {
    const response = await apiClient.get<PublicUserProfileResponse>(
      `/users/${userId}/public-profile`,
    );
    return response.data;
  },

  getCommunityPhotos: async (
    userId: number,
    cursor?: string,
    size = 30,
  ): Promise<UserCommunityPhotosResponse> => {
    const response = await apiClient.get<UserCommunityPhotosResponse>(
      `/users/${userId}/community/posts`,
      { params: { cursor, size } },
    );
    return response.data;
  },

  getWalkPhotos: async (
    userId: number,
    cursor?: string,
    size = 30,
  ): Promise<UserWalkPhotosResponse> => {
    const response = await apiClient.get<UserWalkPhotosResponse>(
      `/users/${userId}/walks/photos`,
      { params: { cursor, size } },
    );
    return response.data;
  },
};
