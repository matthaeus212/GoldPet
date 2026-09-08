import type { WalkPhotoItem } from '../../../../services/walkService';

export function makePhotosWithVariants(count: number): WalkPhotoItem[] {
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    imageUrl: `https://example.com/abc-${i}.jpg?X-Amz-Signature=sig`,
    imageUrlViewer: `https://example.com/abc-${i}_viewer.jpg?X-Amz-Signature=sig`,
    imageUrlMedium: `https://example.com/abc-${i}_medium.jpg?X-Amz-Signature=sig`,
    imageUrlThumb: `https://example.com/abc-${i}_thumb.jpg?X-Amz-Signature=sig`,
    imageKey: `abc-${i}.jpg`,
    imageKeyViewer: `abc-${i}_viewer.jpg`,
    imageKeyThumb: `abc-${i}_thumb.jpg`,
    note: undefined,
    walkDate: '2026-04-17T10:00:00',
    walkId: 1,
    petName: undefined,
    petImageUrl: undefined,
    userId: 1,
    hiddenFromPublic: false,
  }));
}
