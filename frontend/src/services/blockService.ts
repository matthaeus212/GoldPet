import { typedClient } from './api/typedClient';

export interface BlockedUser {
  blockId: number;
  blockedUserId: number;
  blockedNickname: string | null;
  blockedProfileImageUrl: string | null;
  blockedPetName: string | null;
  blockedAt: string | null;
}

export const blockService = {
  getBlockList: async (): Promise<BlockedUser[]> => {
    const response = await typedClient.get('/api/v1/matches/blocks');
    return response.data as BlockedUser[];
  },
  blockUser: async (blockedUserId: number) => {
    return typedClient.post('/api/v1/matches/block', { blockedUserId });
  },
  unblockUser: async (blockedUserId: number) => {
    return typedClient.deletePath('/api/v1/matches/block/{blockedUserId}', { blockedUserId });
  },
};
