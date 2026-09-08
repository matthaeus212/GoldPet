import type { WalkSession } from '../../services/walkService';

export type ShareTarget =
  | { kind: 'walk-summary'; walkId: number; session: WalkSession; watermark: true }
  | { kind: 'walk-photo'; photoUrl: string; walkId?: number; spotId?: number; session?: WalkSession; watermark: boolean }
  | { kind: 'course'; courseId: number; title: string; thumbnailUrl?: string }
  | { kind: 'health-history'; petId: number; petName: string }
  | { kind: 'health-detail'; healthId: number; petName: string; summary: string }
  | { kind: 'community-post'; postId: number; title: string; thumbnailUrl?: string };

export interface ShareResult {
  status: 'shared' | 'dismissed' | 'failed' | 'fallback-copied';
  channel?: string;
}
