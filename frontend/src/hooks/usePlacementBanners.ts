import { useQuery } from '@tanstack/react-query';
import { bannerService } from '../services/bannerService';
import type { PublicBannerResponse, PublicBannersResponse } from '../services/bannerService';

export type BannerPlacement = 'HOME' | 'CHAT' | 'WALK' | 'COMMUNITY';

const PLACEMENT_KEY_MAP: Record<BannerPlacement, keyof PublicBannersResponse> = {
  HOME: 'home',
  CHAT: 'chat',
  WALK: 'walk',
  COMMUNITY: 'community',
};

/**
 * BottomNav 4탭 공유 캐시 훅 — queryKey: ['banners']
 * select로 placement별 슬라이스만 반환. 동시 마운트는 queryKey 공유로 1회 dedupe.
 *
 * staleTime: 0 — 화면 진입/포커스마다 재검증한다. 서버는 Redis 캐시 + `no-cache`+ETag 라
 * 변경 없으면 304(본문 미전송)로 저렴하고, admin 배너 비활성화는 다음 진입에 즉시 반영된다.
 * (max-age 캐시였을 땐 비활성화가 60초간 계속 노출되던 문제 → staleTime 0 + 서버 no-cache 로 해결.)
 * data === undefined → 로딩 중 (미렌더), [] → 빈 배너 (null 렌더)
 */
export function usePlacementBanners(placement: BannerPlacement): PublicBannerResponse[] | undefined {
  const { data } = useQuery({
    queryKey: ['banners'],
    queryFn: bannerService.getPublicBanners,
    staleTime: 0,
    select: (banners) => banners[PLACEMENT_KEY_MAP[placement]],
  });
  return data;
}
