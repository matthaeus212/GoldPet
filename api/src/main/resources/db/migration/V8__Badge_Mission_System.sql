-- Badge Mission System: Add condition metadata for automatic badge awarding

-- 1. Add condition columns to badges table
ALTER TABLE badges ADD COLUMN condition_type VARCHAR(50);
ALTER TABLE badges ADD COLUMN condition_value INT;
ALTER TABLE badges ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Clean FK references before deleting badges
UPDATE challenges SET reward_badge_id = NULL WHERE reward_badge_id IN (SELECT id FROM badges WHERE code IN ('EARLY_BIRD', 'NIGHT_OWL', 'COLLECTOR'));
DELETE FROM user_badges WHERE badge_id IN (SELECT id FROM badges WHERE code IN ('EARLY_BIRD', 'NIGHT_OWL', 'COLLECTOR'));
DELETE FROM badges WHERE code IN ('EARLY_BIRD', 'NIGHT_OWL', 'COLLECTOR');

-- 3. Update existing badges with condition data
UPDATE badges SET condition_type = 'WALK_COUNT', condition_value = 1 WHERE code = 'FIRST_WALK';
UPDATE badges SET condition_type = 'WALK_DISTANCE_TOTAL', condition_value = 10 WHERE code = 'WALKER_10KM';
UPDATE badges SET condition_type = 'WALK_DISTANCE_TOTAL', condition_value = 100 WHERE code = 'WALKER_100KM';
UPDATE badges SET condition_type = 'FRIEND_MATCH', condition_value = 5 WHERE code = 'SOCIAL_BUTTERFLY';

-- 4. Insert new badges (image_url empty; frontend uses emoji fallback)
INSERT INTO badges (code, name, description, image_url, condition_type, condition_value) VALUES
('FIRST_PET', '첫 가족', '첫 반려동물을 등록했어요!', '', 'PET_REGISTER', 1),
('WALK_10', '산책 마니아', '산책 10회 달성!', '', 'WALK_COUNT', 10),
('FIRST_POST', '소통의 시작', '첫 커뮤니티 글을 작성했어요!', '', 'COMMUNITY_POST', 1),
('COMMUNITY_STAR', '커뮤니티 스타', '커뮤니티 게시글 10개 작성', '', 'COMMUNITY_POST', 10),
('FIRST_COMMENT', '공감의 한마디', '첫 댓글을 달았어요!', '', 'COMMUNITY_COMMENT', 1),
('CHECK_IN_5', '핫플 탐험가', '체크인 5회 달성', '', 'CHECK_IN', 5);
