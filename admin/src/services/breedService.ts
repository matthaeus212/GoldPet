// Breed Service - API integration for Admin breed management

import { typedClient } from './typedClient';
import type {
  BreedResponse,
  SpeciesResponse,
  CreateBreedRequest,
  UpdateBreedRequest,
} from '../types/api';

// Re-export under historical local names (Phase 3.11.1).
export type { BreedResponse, SpeciesResponse, CreateBreedRequest, UpdateBreedRequest };

export const breedService = {
  // Get all species
  getSpecies: async (): Promise<SpeciesResponse[]> => {
    const response = await typedClient.get('/api/v1/admin/data/species');
    return response.data as SpeciesResponse[];
  },

  // Get all breeds (optionally filtered by species)
  getBreeds: async (speciesId?: number): Promise<BreedResponse[]> => {
    const response = await typedClient.get('/api/v1/admin/breeds', {
      params: speciesId ? { speciesId } : undefined,
    });
    return response.data as BreedResponse[];
  },

  // Get single breed
  getBreed: async (breedId: number): Promise<BreedResponse> => {
    const response = await typedClient.getPath(
      '/api/v1/admin/breeds/{breedId}',
      { breedId },
    );
    return response.data as BreedResponse;
  },

  // Create breed
  createBreed: async (data: CreateBreedRequest): Promise<BreedResponse> => {
    const response = await typedClient.post('/api/v1/admin/breeds', data);
    return response.data as BreedResponse;
  },

  // Update breed
  updateBreed: async (breedId: number, data: UpdateBreedRequest): Promise<BreedResponse> => {
    const response = await typedClient.putPath(
      '/api/v1/admin/breeds/{breedId}',
      { breedId },
      data,
    );
    return response.data as BreedResponse;
  },

  // Delete breed
  deleteBreed: async (breedId: number): Promise<void> => {
    await typedClient.deletePath(
      '/api/v1/admin/breeds/{breedId}',
      { breedId },
    );
  },

  // Create species
  createSpecies: async (data: { name: string; code: string }): Promise<SpeciesResponse> => {
    const response = await typedClient.post(
      '/api/v1/admin/data/species',
      data as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/data/species'>>[1],
    );
    return response.data as SpeciesResponse;
  },

  // Delete species
  deleteSpecies: async (speciesId: number): Promise<void> => {
    await typedClient.deletePath(
      '/api/v1/admin/data/species/{id}',
      { id: speciesId },
    );
  },
};
