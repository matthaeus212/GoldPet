import { typedClient } from './api/typedClient';
import type { components } from '../api/schema.d';

export type PublicBannerResponse = components['schemas']['PublicBannerResponse'];
export type PublicBannersResponse = components['schemas']['PublicBannersResponse'];

export const bannerService = {
  getPublicBanners: async (): Promise<PublicBannersResponse> => {
    const response = await typedClient.get('/api/v1/banners');
    return response.data;
  },
};
