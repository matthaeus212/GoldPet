import { typedClient } from './typedClient';
import type {
  PetAttributeResponse,
  AttributeOption,
  PetAttributeRequest,
} from '../types/api';

// Generated aliases (Phase 3.11.1). Backend uses single PetAttributeRequest for
// both create + update; matches Partial<...> semantics at the call site.
export type { AttributeOption };
export type PetAttribute = PetAttributeResponse;
export type CreateAttributeRequest = PetAttributeRequest;
export type UpdateAttributeRequest = PetAttributeRequest;

export const getAttributes = async (): Promise<PetAttribute[]> => {
    const response = await typedClient.get('/api/v1/admin/pet-attributes');
    return response.data as PetAttribute[];
};

export const createAttribute = async (data: CreateAttributeRequest): Promise<PetAttribute> => {
    const response = await typedClient.post('/api/v1/admin/pet-attributes', data);
    return response.data as PetAttribute;
};

export const updateAttribute = async (id: number, data: UpdateAttributeRequest): Promise<PetAttribute> => {
    const response = await typedClient.putPath(
        '/api/v1/admin/pet-attributes/{id}',
        { id },
        data,
    );
    return response.data as PetAttribute;
};

export const deleteAttribute = async (id: number): Promise<void> => {
    await typedClient.deletePath(
        '/api/v1/admin/pet-attributes/{id}',
        { id },
    );
};
