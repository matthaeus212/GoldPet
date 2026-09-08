-- =================================================================================================
-- 1. Mock Users (Seoul Area)
-- =================================================================================================

-- User 1: Gangnam (Active Walker)
INSERT INTO users (nickname, email, oauth_provider, oauth_id, main_location_geom, gold_balance) VALUES
('강남산책러', 'gangnam@test.com', 'KAKAO', '123456789', ST_SetSRID(ST_MakePoint(127.027, 37.497), 4326), 5000);

-- User 2: Hongdae (Cat Lover)
INSERT INTO users (nickname, email, oauth_provider, oauth_id, main_location_geom, gold_balance) VALUES
('홍대고양이', 'hongdae@test.com', 'NAVER', '987654321', ST_SetSRID(ST_MakePoint(126.924, 37.556), 4326), 3000);

-- User 3: Yeouido (Morning Walker)
INSERT INTO users (nickname, email, oauth_provider, oauth_id, main_location_geom, gold_balance) VALUES
('한강러닝', 'yeouido@test.com', 'GOOGLE', '1122334455', ST_SetSRID(ST_MakePoint(126.934, 37.528), 4326), 10000);

-- User 4: Seongsu (Social User)
INSERT INTO users (nickname, email, oauth_provider, oauth_id, main_location_geom, gold_balance) VALUES
('성수힙스터', 'seongsu@test.com', 'APPLE', '5566778899', ST_SetSRID(ST_MakePoint(127.056, 37.544), 4326), 2000);

-- =================================================================================================
-- 2. Mock Pets
-- =================================================================================================

-- User 1's Dog
INSERT INTO pets (owner_user_id, name, species_id, breed_id, gender, birth_date, weight_kg, is_neutered, temperament_tags)
VALUES (
    (SELECT id FROM users WHERE nickname = '강남산책러'),
    '초코',
    (SELECT id FROM pet_species WHERE code = 'DOG'),
    (SELECT id FROM pet_breeds WHERE name = '골든 리트리버'),
    'MALE', '2023-01-01', 25.5, true, '활발,친화적'
);

-- User 2's Cat
INSERT INTO pets (owner_user_id, name, species_id, breed_id, gender, birth_date, weight_kg, is_neutered, temperament_tags)
VALUES (
    (SELECT id FROM users WHERE nickname = '홍대고양이'),
    '나비',
    (SELECT id FROM pet_species WHERE code = 'CAT'),
    (SELECT id FROM pet_breeds WHERE name = '코리안 숏헤어'),
    'FEMALE', '2022-05-05', 4.2, true, '도도,개냥이'
);

-- User 3's Dog
INSERT INTO pets (owner_user_id, name, species_id, breed_id, gender, birth_date, weight_kg, is_neutered, temperament_tags)
VALUES (
    (SELECT id FROM users WHERE nickname = '한강러닝'),
    '구름',
    (SELECT id FROM pet_species WHERE code = 'DOG'),
    (SELECT id FROM pet_breeds WHERE name = '비숑 프리제'),
    'FEMALE', '2024-03-01', 5.0, false, '애교,겁쟁이'
);

-- =================================================================================================
-- 3. Mock Walks
-- =================================================================================================

-- User 1 Walk in Gangnam
INSERT INTO walks (user_id, start_time, end_time, distance_km, duration_seconds, path)
VALUES (
    (SELECT id FROM users WHERE nickname = '강남산책러'),
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day' + INTERVAL '30 minutes',
    1.5,
    1800,
    ST_SetSRID(ST_MakeLine(ARRAY[ST_MakePoint(127.027, 37.497), ST_MakePoint(127.028, 37.498), ST_MakePoint(127.029, 37.499)]), 4326)
);

-- =================================================================================================
-- 4. Mock Social Interactions
-- =================================================================================================

-- User 1 likes User 3
INSERT INTO likes (from_user_id, to_user_id, status) VALUES
(
    (SELECT id FROM users WHERE nickname = '강남산책러'),
    (SELECT id FROM users WHERE nickname = '한강러닝'),
    'ACTIVE'
);

-- User 3 likes User 1 (Match!)
INSERT INTO likes (from_user_id, to_user_id, status) VALUES
(
    (SELECT id FROM users WHERE nickname = '한강러닝'),
    (SELECT id FROM users WHERE nickname = '강남산책러'),
    'ACTIVE'
);

-- Create Match
INSERT INTO matches (user1_id, user2_id)
SELECT
    LEAST(u1.id, u2.id),
    GREATEST(u1.id, u2.id)
FROM users u1, users u2
WHERE u1.nickname = '한강러닝' AND u2.nickname = '강남산책러'
ON CONFLICT DO NOTHING;

-- =================================================================================================
-- 5. Mock Community Posts
-- =================================================================================================

INSERT INTO community_posts (user_id, category_id, title, content, post_type) VALUES
(
    (SELECT id FROM users WHERE nickname = '강남산책러'),
    (SELECT id FROM community_categories WHERE code = 'WALK'),
    '오늘 날씨 정말 좋네요!',
    '초코랑 산책하기 딱 좋은 날씨입니다. 다들 나오세요~',
    'GENERAL'
),
(
    (SELECT id FROM users WHERE nickname = '홍대고양이'),
    (SELECT id FROM community_categories WHERE code = 'QNA'),
    '고양이 사료 추천 부탁드려요',
    '입맛이 까다로운 아이라 고민입니다.',
    'QUESTION'
);

-- =================================================================================================
-- 6. Gamification Test Data (from V9)
-- =================================================================================================

-- Seed Challenge
INSERT INTO challenges (title, description, goal_type, goal_value, reward_gold, start_date, end_date, is_active)
VALUES
('Weekly 10km Walk', 'Walk 10km this week!', 'WALK_DISTANCE_TOTAL', 10000, 500, NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', true);
