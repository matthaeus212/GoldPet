import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';

export interface PublicSettings {
    communityPostPreviewLength: number;
    /** Feature flag: community author profile link (default false until backend seeds true) */
    authorProfileLinkEnabled?: boolean;
}

export const systemService = {
    getPublicSettings: async (): Promise<PublicSettings> => {
        try {
            const response = await axios.get(`${API_BASE_URL}/api/v1/settings/public`);
            return response.data;
        } catch (error) {
            console.error('Failed to fetch public settings:', error);
            // Return safe default if fetch fails
            return { communityPostPreviewLength: 100 };
        }
    }
};
