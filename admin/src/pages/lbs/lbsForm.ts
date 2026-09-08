export interface PlaceForm {
  name: string;
  category: string;
  latitude: string;
  longitude: string;
}

export interface CreatePlacePayload {
  name: string;
  category: string;
  latitude: number;
  longitude: number;
}

export interface UpdatePlacePayload {
  name?: string;
  category?: string;
  latitude?: number;
  longitude?: number;
}

export class PlaceFormError extends Error {}

export function buildCreatePlacePayload(form: PlaceForm): CreatePlacePayload {
  const latitude = parseFloat(form.latitude);
  const longitude = parseFloat(form.longitude);
  if (isNaN(latitude) || isNaN(longitude)) {
    throw new PlaceFormError('위도/경도를 올바르게 입력해주세요.');
  }
  return {
    name: form.name,
    category: form.category,
    latitude,
    longitude,
  };
}

export function buildUpdatePlacePayload(form: PlaceForm): UpdatePlacePayload {
  const latitude = form.latitude !== '' ? parseFloat(form.latitude) : undefined;
  const longitude = form.longitude !== '' ? parseFloat(form.longitude) : undefined;
  if (latitude !== undefined && isNaN(latitude)) {
    throw new PlaceFormError('위도를 올바르게 입력해주세요.');
  }
  if (longitude !== undefined && isNaN(longitude)) {
    throw new PlaceFormError('경도를 올바르게 입력해주세요.');
  }
  return {
    name: form.name || undefined,
    category: form.category || undefined,
    latitude,
    longitude,
  };
}
