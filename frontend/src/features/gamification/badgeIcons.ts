import firstWalk from '../../assets/badges/badge-first-walk.svg';
import neighborhood from '../../assets/badges/badge-neighborhood.svg';
import marathoner from '../../assets/badges/badge-marathoner.svg';
import popular from '../../assets/badges/badge-popular.svg';
import firstFamily from '../../assets/badges/badge-first-family.svg';
import walkMania from '../../assets/badges/badge-walk-mania.svg';
import firstPost from '../../assets/badges/badge-first-post.svg';
import communityStar from '../../assets/badges/badge-community-star.svg';
import empathy from '../../assets/badges/badge-empathy.svg';
import hotplace from '../../assets/badges/badge-hotplace.svg';

// Seed 뱃지(id) → 번들된 SVG fallback
// Source of truth: api/src/main/resources/db/migration/V2 + V8
// 관리자가 imageUrl을 업로드하면 그 URL이 우선 사용됨
const BADGE_FALLBACK_BY_ID: Record<number, string> = {
  1: firstWalk,       // 첫 걸음
  2: neighborhood,    // 동네 한 바퀴
  3: marathoner,      // 마라토너
  4: popular,         // 핵인싸
  8: firstFamily,     // 첫 가족
  9: walkMania,       // 산책 마니아
  10: firstPost,      // 소통의 시작
  11: communityStar,  // 커뮤니티 스타
  12: empathy,        // 공감의 한마디
  13: hotplace,       // 핫플 탐험가
};

/**
 * 뱃지 아이콘 URL 해석 3단 fallback.
 * 1) badge.imageUrl이 있으면 그대로 사용 (관리자 업로드)
 * 2) 없으면 seed 뱃지 id 기반 번들 SVG
 * 3) 그것도 없으면 null (호출부에서 emoji fallback)
 */
export function getBadgeIcon(badge: { id: number; imageUrl?: string | null } | null | undefined): string | null {
  if (!badge) return null;
  if (badge.imageUrl && badge.imageUrl.trim()) return badge.imageUrl;
  return BADGE_FALLBACK_BY_ID[badge.id] ?? null;
}
