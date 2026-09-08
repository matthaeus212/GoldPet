import { typedClient } from './typedClient';

export interface DataItem {
    id: number;
    name: string;
    orderIndex?: number;
    code?: string;
}

export const dataService = {
    // Interests
    getInterests: async (): Promise<DataItem[]> => {
        const response = await typedClient.get('/api/v1/admin/data/interests');
        return response.data as DataItem[];
    },

    createInterest: async (name: string, orderIndex?: number): Promise<DataItem> => {
        const body = { name, orderIndex } as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/data/interests'>>[1];
        const response = await typedClient.post('/api/v1/admin/data/interests', body);
        return response.data as DataItem;
    },

    deleteInterest: async (id: number): Promise<void> => {
        await typedClient.deletePath('/api/v1/admin/data/interests/{id}', { id });
    },

    // Hobbies
    getHobbies: async (): Promise<DataItem[]> => {
        const response = await typedClient.get('/api/v1/admin/data/hobbies');
        return response.data as DataItem[];
    },

    createHobby: async (name: string, orderIndex?: number): Promise<DataItem> => {
        const body = { name, orderIndex } as unknown as Parameters<typeof typedClient.post<'/api/v1/admin/data/hobbies'>>[1];
        const response = await typedClient.post('/api/v1/admin/data/hobbies', body);
        return response.data as DataItem;
    },

    deleteHobby: async (id: number): Promise<void> => {
        await typedClient.deletePath('/api/v1/admin/data/hobbies/{id}', { id });
    },

    // Community Categories
    getCategories: async (): Promise<DataItem[]> => {
        const response = await typedClient.get('/api/v1/admin/data/categories');
        return response.data as DataItem[];
    },

    createCategory: async (name: string, code?: string): Promise<DataItem> => {
        const response = await typedClient.post('/api/v1/admin/data/categories', { name, code });
        return response.data as DataItem;
    },

    deleteCategory: async (id: number): Promise<void> => {
        await typedClient.deletePath('/api/v1/admin/data/categories/{id}', { id });
    },
};
