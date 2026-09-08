// 체크인/장소 API 클라이언트
import { typedClient } from './api/typedClient';
import type {
  PlaceResponse,
  CheckInResponse,
  CreateCheckInRequest as CreateCheckInRequestDto,
  CreatePlaceRequest as CreatePlaceRequestDto,
} from '../types/api';

// Generated aliases (Phase 3.10).
export type Place = PlaceResponse;
export type CheckIn = CheckInResponse;
export type CreateCheckInRequest = CreateCheckInRequestDto;
export type CreatePlaceRequest = CreatePlaceRequestDto;

/**
 * STYLE-003: 이 파일에는 VITE_USE_MOCK 스캐폴딩과 목 데이터가 있었다. 플래그는 4개 env 모두
 * 'false' 로 고정돼 목 분기 자체는 도달 불가였지만, **목 데이터가 catch 블록의 폴백으로도
 * 쓰이고 있었다.** 그 결과:
 *  - 서버가 죽어도 사용자에게 가짜 장소·가짜 체크인 목록이 보였고,
 *  - `checkIn`/`createPlace` 는 API 실패 시 **가짜 성공 객체를 반환**해 사용자는 체크인이 됐다고
 *    믿었지만 서버에는 아무것도 저장되지 않았다(조용한 실패).
 *  - PlaceListPage 에는 이미 에러/빈 상태 UI 가 있는데 폴백 때문에 절대 표시되지 않았다.
 *
 * 이제 실패는 그대로 던진다. React Query 가 에러로 잡고 화면이 정직하게 알린다.
 */
export const checkinService = {
  getNearbyPlaces: async (
    lat: number,
    lng: number,
    radiusMeters = 1000
  ): Promise<Place[]> => {
    const response = await typedClient.get('/api/v1/checkin/places/nearby', {
      params: { latitude: lat, longitude: lng, radiusMeters },
    });
    return response.data as Place[];
  },

  getPlaces: async (category?: string, page = 0, size = 20): Promise<Place[]> => {
    const response = await typedClient.get('/api/v1/checkin/places', {
      params: { category, page, size },
    });
    return response.data as Place[];
  },

  getPlace: async (placeId: number): Promise<Place> => {
    const response = await typedClient.getPath(
      '/api/v1/checkin/places/{placeId}',
      { placeId },
    );
    return response.data as Place;
  },

  searchPlaces: async (name: string, page = 0, size = 20): Promise<Place[]> => {
    const response = await typedClient.get('/api/v1/checkin/places/search', {
      params: { name, page, size },
    });
    return response.data as Place[];
  },

  createPlace: async (request: CreatePlaceRequest): Promise<Place> => {
    const response = await typedClient.post('/api/v1/checkin/places', request);
    return response.data as Place;
  },

  checkIn: async (request: CreateCheckInRequest): Promise<CheckIn> => {
    const response = await typedClient.post('/api/v1/checkin', request);
    return response.data as CheckIn;
  },

  getMyCheckIns: async (page = 0, size = 20): Promise<CheckIn[]> => {
    const response = await typedClient.get('/api/v1/checkin/my', {
      params: { page, size },
    });
    return response.data as CheckIn[];
  },

  getPlaceCheckIns: async (
    placeId: number,
    page = 0,
    size = 20
  ): Promise<CheckIn[]> => {
    const response = await typedClient.getPath(
      '/api/v1/checkin/places/{placeId}/checkins',
      { placeId },
      { params: { page, size } },
    );
    return response.data as CheckIn[];
  },

  getCheckInCount: async (): Promise<number> => {
    const response = await typedClient.get('/api/v1/checkin/count');
    return (response.data as { count: number }).count;
  },
};
