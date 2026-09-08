-- 1. 커뮤니티 게시글/댓글 숨김 필드 추가
ALTER TABLE community_posts ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE community_comments ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT false;

-- 2. 숨김 필터링 성능을 위한 부분 인덱스
CREATE INDEX idx_community_posts_hidden ON community_posts (is_hidden) WHERE is_hidden = false;
CREATE INDEX idx_community_comments_hidden ON community_comments (is_hidden) WHERE is_hidden = false;

-- 3. 자동 제재 임계값 설정
INSERT INTO system_settings (setting_key, setting_value, description)
VALUES ('REPORT_AUTO_HIDE_THRESHOLD', '5', '동일 대상 신고 누적 시 자동 숨김 처리 임계값 (0=비활성화)')
ON CONFLICT (setting_key) DO NOTHING;
