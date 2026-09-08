import type { ShareTarget } from './types';

export function shouldWatermark(target: ShareTarget): boolean {
  switch (target.kind) {
    case 'walk-summary':
      return true;
    case 'walk-photo':
      return target.watermark === true;
    case 'health-detail':
    case 'health-history':
      return true;
    case 'course':
      return Boolean(target.thumbnailUrl);
    case 'community-post':
      return false;
  }
}
