import { typedClient } from './typedClient';

export interface LBSStats {
  totalWalks: number;
  todayWalks: number;
  totalDistance: number;
  avgDuration: number;
  topSpots: { id: number; name: string; visits: number }[];
}

export interface Place {
  id: number;
  name: string;
  category: string;
  latitude: number;
  longitude: number;
  visits: number;
  rating: number;
}

export interface PlaceCategoryOption {
  value: string;
  displayName: string;
}

export const lbsService = {
  async getPlaceCategories(): Promise<PlaceCategoryOption[]> {
    const response = await typedClient.get('/api/v1/admin/lbs/place-categories');
    return response.data as unknown as PlaceCategoryOption[];
  },

  async getStats(): Promise<LBSStats> {
    const response = await typedClient.get('/api/v1/admin/lbs/stats');
    return response.data as unknown as LBSStats;
  },

  async getPlaces(): Promise<Place[]> {
    const response = await typedClient.get('/api/v1/admin/lbs/places');
    return response.data as unknown as Place[];
  },

  async createPlace(data: { name: string; category: string; latitude: number; longitude: number }): Promise<Place> {
    const body = data as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/lbs/places'>>[1];
    const response = await typedClient.post('/api/v1/admin/lbs/places', body);
    return response.data as unknown as Place;
  },

  async updatePlace(id: number, req: { name?: string; category?: string; latitude?: number; longitude?: number }): Promise<Place> {
    const body = req as unknown as Parameters<typeof typedClient.putPath<'/api/v1/admin/lbs/places/{id}'>>[2];
    const response = await typedClient.putPath('/api/v1/admin/lbs/places/{id}', { id }, body);
    return response.data as unknown as Place;
  },

  async deletePlace(id: number): Promise<void> {
    await typedClient.deletePath('/api/v1/admin/lbs/places/{id}', { id });
  },
};
