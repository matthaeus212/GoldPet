/**
 * Derives variant URLs from an original image URL.
 *
 * Convention: original is `{uuid}.{ext}`, variants are `{uuid}_thumb.jpg` and `{uuid}_medium.jpg`.
 * For URLs that don't match this pattern (legacy, external), returns the original unchanged.
 *
 * NOTE: 레거시 이미지(마이그레이션 이전 업로드)는 variant가 S3에 없으므로
 * 브라우저가 404를 받고 onerror fallback으로 원본을 로드합니다.
 * 이로 인해 레거시 이미지당 1회 추가 HTTP 요청(404)이 발생합니다.
 * 향후 backfill 스크립트로 기존 이미지의 variant를 생성하면 해소됩니다.
 */
export function getImageUrl(
  originalUrl: string,
  variant: 'original' | 'medium' | 'thumbnail' = 'original'
): string {
  if (variant === 'original') return originalUrl;

  // GIF files: return original (animated GIFs have no static thumbnail)
  if (originalUrl.toLowerCase().endsWith('.gif')) return originalUrl;

  // Match pattern: http(s)://host/bucket/{uuid}.{ext}
  const match = originalUrl.match(/^(.+\/[0-9a-f-]{36})\.[a-z]+$/i);
  if (!match) return originalUrl;  // Legacy or external URL -- use as-is

  const suffix = variant === 'thumbnail' ? '_thumb' : '_medium';
  return `${match[1]}${suffix}.jpg`;
}
