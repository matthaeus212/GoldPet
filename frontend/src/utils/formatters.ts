/**
 * Truncate location text to city/district level (drop dong/neighborhood).
 * Input format: "{province} {city} {dong}" from LocationSettingPage structured picker.
 * Example: "서울 강남구 논현동" → "서울 강남구"
 * Example: "서울 강남구" → "서울 강남구" (already 2 parts)
 * Example: undefined → "위치 미설정"
 * Example: "" → "위치 미설정"
 */
export function truncateLocation(locationText?: string | null, fallback = '위치 미설정'): string {
  if (!locationText || !locationText.trim()) return fallback;
  const parts = locationText.trim().split(' ');
  if (parts.length <= 2) return locationText.trim();
  return parts.slice(0, 2).join(' ');
}

/**
 * Convert exact age to decade band for privacy.
 * Example: 27 → "20대", 35 → "30대", 9 → "0대"
 * Example: undefined → ""
 */
export function formatAgeGroup(age?: number | null): string {
  if (age == null) return '';
  return `${Math.floor(age / 10) * 10}대`;
}

/**
 * Format distance in meters to km with 1 decimal place.
 * Always shows km, never meters.
 * Example: 500 → "0.5km", 1234 → "1.2km", 0 → "0.0km"
 */
export function formatDistanceKm(meters: number): string {
  return `${(meters / 1000).toFixed(1)}km`;
}
