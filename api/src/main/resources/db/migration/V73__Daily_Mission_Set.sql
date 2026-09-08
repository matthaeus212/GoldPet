-- Wave 2b: Daily Missions
-- 미션 = repeatable DAILY 배지의 표현 (신규 보상경로 없음 — BadgeAwardService award+gold 재사용).
-- 유일한 신규 인프라: daily_mission_set — 그날 노출할 회전 배지 묶음(전 유저 공통).
-- 진행도는 user_badges(DAILY cycleKey) 읽기 조인.

CREATE TABLE daily_mission_set (
    id           BIGSERIAL PRIMARY KEY,
    mission_date DATE  NOT NULL UNIQUE,
    badge_ids    JSONB NOT NULL
);

-- 데일리 미션 후보 배지 시드 (conditionType ∈ {WALK_DISTANCE_TOTAL, COMMUNITY_POST, CHECK_IN}).
-- 회전 풀 rewardGold 합(30)이 20골드 캡을 초과 → 지급 시 BadgeAwardService가 하루 20골드로 캡.
INSERT INTO badges (name, description, image_url, condition_type, condition_value, is_active, reward_gold, is_repeatable, repeat_cycle) VALUES
('오늘의 산책', '오늘 산책으로 누적 거리를 늘려보세요!', '', 'WALK_DISTANCE_TOTAL', 1, TRUE, 10, TRUE, 'DAILY'),
('오늘의 소통', '오늘 커뮤니티에 글을 작성해보세요!', '', 'COMMUNITY_POST', 1, TRUE, 10, TRUE, 'DAILY'),
('오늘의 체크인', '오늘 장소에 체크인 해보세요!', '', 'CHECK_IN', 1, TRUE, 10, TRUE, 'DAILY');
