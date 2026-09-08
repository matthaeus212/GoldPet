-- Option B Phase 3: youtube_url 컬럼 철거
-- Phase 0 실측(2026-04-17, dev DB)에서 youtube_url 보유 게시글 0건 확인 → 데이터 손실 없음
-- 이전 Phase 1(c23d59a) + Phase 2(04022e5)로 모든 코드 참조 제거 완료
ALTER TABLE community_posts DROP COLUMN youtube_url;
