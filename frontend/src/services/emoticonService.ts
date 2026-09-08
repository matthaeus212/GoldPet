import { typedClient } from './api/typedClient';
import type { EmoticonResponse } from '../types/api';

// Generated alias (Phase 3.10).
export type Emoticon = EmoticonResponse;

export const emoticonService = {
  getEmoticons: async (): Promise<Emoticon[]> => {
    const response = await typedClient.get('/api/v1/emoticons');
    return response.data;
  }
};
