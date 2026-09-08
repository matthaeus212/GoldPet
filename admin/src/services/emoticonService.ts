import { typedClient } from './typedClient';
import type {
  EmoticonResponse,
  EmoticonPackResponse,
  CreateEmoticonRequest,
  UpdateEmoticonRequest,
} from '../types/api';

// Re-export under historical local names (Phase 3.11.1).
export type {
  EmoticonResponse,
  EmoticonPackResponse,
  CreateEmoticonRequest,
  UpdateEmoticonRequest,
};

export const emoticonService = {
  getAll: async (): Promise<EmoticonResponse[]> => {
    const response = await typedClient.get('/api/v1/admin/emoticons');
    return response.data as EmoticonResponse[];
  },

  getPacks: async (): Promise<EmoticonPackResponse[]> => {
    const response = await typedClient.get('/api/v1/admin/emoticons/packs');
    return response.data as EmoticonPackResponse[];
  },

  create: async (data: CreateEmoticonRequest): Promise<EmoticonResponse> => {
    const response = await typedClient.post('/api/v1/admin/emoticons', data);
    return response.data as EmoticonResponse;
  },

  update: async (id: number, data: UpdateEmoticonRequest): Promise<EmoticonResponse> => {
    const response = await typedClient.putPath(
      '/api/v1/admin/emoticons/{id}',
      { id },
      data,
    );
    return response.data as EmoticonResponse;
  },

  delete: async (id: number): Promise<void> => {
    await typedClient.deletePath('/api/v1/admin/emoticons/{id}', { id });
  },

  toggleActive: async (id: number): Promise<EmoticonResponse> => {
    const response = await typedClient.patchPath(
      '/api/v1/admin/emoticons/{id}/toggle',
      { id },
      undefined,
    );
    return response.data as EmoticonResponse;
  },
};
