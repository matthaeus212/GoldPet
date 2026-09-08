-- community-author-profile-gallery Phase 1 — 피처 플래그 seed.
--
-- community.author_profile_link.enabled — 프론트 프로필 탭 → `/users/:userId` 라우트 진입 피처 플래그.
-- 기본값 false. roll-out 은 admin 콘솔 또는 `SystemSettingService.setValue(..)` 로 수동 ON 후 48h 메트릭 관찰.
--
-- V58 의 `CREATE INDEX CONCURRENTLY` 는 non-transactional 이므로, transactional INSERT 는 본 파일(V59)로 분리.
INSERT INTO system_settings (setting_key, setting_value, description)
VALUES (
    'community.author_profile_link.enabled',
    'false',
    '커뮤니티 작성자 아바타 탭 → 공개 프로필 갤러리 페이지 진입 허용 여부 (community-author-profile-gallery Phase 1)'
)
ON CONFLICT (setting_key) DO NOTHING;
