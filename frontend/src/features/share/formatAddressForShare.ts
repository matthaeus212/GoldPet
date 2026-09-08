/**
 * 출발지 주소에서 "동" 단위까지만 추출 (개인정보 보호 — §4 Guardrails)
 *
 * Examples:
 *   "서울특별시 강남구 역삼동 123-45"  → "역삼동"
 *   "경기도 성남시 분당구 정자1동 170" → "정자1동"
 *   "서울특별시 강남구 테헤란로 152"   → "강남구"  (로-based: fall back to 구/군)
 */
export function formatAddressForShare(address: string): string {
  if (!address) return '';

  // 1. 동/읍/면/리 단위 우선 추출
  const dongMatch = address.match(/(\S+[동읍면리])(?:\s|$)/);
  if (dongMatch) return dongMatch[1];

  // 2. 로/대로 주소는 구/군 단위로 폴백
  const guMatch = address.match(/(\S+[구군])(?:\s|$)/);
  if (guMatch) return guMatch[1];

  // 3. 첫 두 토큰 반환
  return address.split(' ').slice(0, 2).join(' ');
}
