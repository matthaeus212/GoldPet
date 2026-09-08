
import apiClient from './api/client';
import { typedClient } from './api/typedClient';
import { type User } from '../stores/authStore';
import type { UserStats } from '../types/api';

// Re-export for existing importers.
export type { UserStats };

export interface UpdateProfileRequest {
    nickname?: string;
    // email is usually read-only or handled separately
    imageUrls?: string[];
    name?: string;
    birthDate?: string;
    phoneNumber?: string;
    gender?: string;
    hasPet?: boolean;
    intro?: string;
    mbti?: string;
    interests?: string[];
    hobbies?: string[];
}

export const userService = {
    getMe: async (): Promise<User> => {
        try {
            const response = await typedClient.get('/api/v1/users/me');
            return response.data as unknown as User;
        } catch (error: unknown) {
            const err = error as { response?: { data?: { message?: string } } };
            throw new Error(err.response?.data?.message || '사용자 정보를 가져오는데 실패했습니다.');
        }
    },

    updateProfile: async (data: UpdateProfileRequest): Promise<User> => {
        try {
            // Spec marks interests/hobbies required; backend accepts partial updates.
            const body = data as unknown as Parameters<typeof typedClient.put<'/api/v1/users/me'>>[1];
            const response = await typedClient.put('/api/v1/users/me', body);
            return response.data as unknown as User;
        } catch (error: unknown) {
            const err = error as { response?: { data?: { message?: string } } };
             throw new Error(err.response?.data?.message || '프로필 수정에 실패했습니다.');
        }
    },

    updateProfileImages: async (imageUrls: string[]): Promise<User> => {
        try {
            const response = await typedClient.put(
                '/api/v1/users/me/profile-image',
                { profileImageUrls: imageUrls },
            );
            return response.data as unknown as User;
        } catch (error: unknown) {
            const err = error as { response?: { data?: { message?: string } } };
             throw new Error(err.response?.data?.message || '프로필 이미지 수정에 실패했습니다.');
        }
    },

    uploadImage: async (file: File): Promise<string> => {
        try {
            const { compressImage } = await import('../utils/imageCompression');
            const compressed = await compressImage(file);
            const formData = new FormData();
            formData.append('file', compressed);
            formData.append('category', 'profile');
            // Multipart — stays on apiClient; typedClient is JSON-only.
            const response = await apiClient.post('/files/upload', formData, {
                headers: { 'Content-Type': 'multipart/form-data' },
            });
            return response.data.url;
        } catch {
             throw new Error('이미지 업로드에 실패했습니다.');
        }
    },

    getInterests: async (): Promise<{id: number, name: string}[]> => {
        try {
            const response = await typedClient.get('/api/v1/users/interests');
            return response.data as { id: number; name: string }[];
        } catch (error: unknown) {
            console.error('Failed to fetch interests', error);
            throw error;
        }
    },

    getHobbies: async (): Promise<{id: number, name: string}[]> => {
        try {
            const response = await typedClient.get('/api/v1/users/hobbies');
            return response.data as { id: number; name: string }[];
        } catch (error: unknown) {
            console.error('Failed to fetch hobbies', error);
            throw error;
        }
    },

    checkNickname: async (nickname: string): Promise<boolean> => {
        try {
            const response = await typedClient.get('/api/v1/users/check-nickname', {
                params: { nickname },
            });
            return (response.data as { available: boolean }).available;
        } catch (error: unknown) {
            console.error('Failed to check nickname:', error);
            throw error;
        }
    },

    requestVerificationCode: async (phoneNumber: string): Promise<string | null> => {
        try {
            const response = await typedClient.post(
                '/api/v1/auth/verification/request',
                { phoneNumber },
            );
            return (response.data as { code?: string }).code || null;
        } catch (error: unknown) {
            console.error('Failed to request verification code:', error);
            throw error;
        }
    },

    confirmVerificationCode: async (phoneNumber: string, code: string): Promise<boolean> => {
        try {
            const response = await typedClient.post(
                '/api/v1/auth/verification/confirm',
                { phoneNumber, code },
            );
            return (response.data as { success: boolean }).success;
        } catch (error: unknown) {
            console.error('Failed to confirm verification code:', error);
            return false;
        }
    },

    updateNotification: async (enabled: boolean): Promise<User> => {
        try {
            const response = await typedClient.patch(
                '/api/v1/users/me/notification',
                { enabled },
            );
            return response.data as unknown as User;
        } catch (error: unknown) {
            const err = error as { response?: { data?: { message?: string } } };
             throw new Error(err.response?.data?.message || '알림 설정 변경에 실패했습니다.');
        }
    },

    getMyStats: async (): Promise<UserStats> => {
        const response = await typedClient.get('/api/v1/users/me/stats');
        return response.data as UserStats;
    },

    changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
        await typedClient.put('/api/v1/users/me/password', { currentPassword, newPassword });
    },
};
