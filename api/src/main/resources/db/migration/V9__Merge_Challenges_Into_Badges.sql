-- V9: Merge Challenges into Badge Mission System
-- 1) Remove code column from badges
ALTER TABLE badges DROP CONSTRAINT IF EXISTS badges_code_key;
ALTER TABLE badges DROP COLUMN IF EXISTS code;

-- 2) Add new fields to badges
ALTER TABLE badges ADD COLUMN IF NOT EXISTS reward_gold INT DEFAULT NULL;
ALTER TABLE badges ADD COLUMN IF NOT EXISTS start_date TIMESTAMP DEFAULT NULL;
ALTER TABLE badges ADD COLUMN IF NOT EXISTS end_date TIMESTAMP DEFAULT NULL;
ALTER TABLE badges ADD COLUMN IF NOT EXISTS is_repeatable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE badges ADD COLUMN IF NOT EXISTS repeat_cycle VARCHAR(20) DEFAULT NULL;

-- 3) Extend user_badges
ALTER TABLE user_badges ADD COLUMN IF NOT EXISTS current_value INT NOT NULL DEFAULT 0;
ALTER TABLE user_badges ADD COLUMN IF NOT EXISTS cycle_key VARCHAR(20) DEFAULT NULL;
ALTER TABLE user_badges ADD COLUMN IF NOT EXISTS reward_gold_given INT NOT NULL DEFAULT 0;

-- Change unique constraint to include cycle_key
ALTER TABLE user_badges DROP CONSTRAINT IF EXISTS user_badges_user_id_badge_id_key;
ALTER TABLE user_badges ADD CONSTRAINT user_badges_user_badge_cycle_key UNIQUE (user_id, badge_id, cycle_key);

-- 4) Copy reward_gold from challenges to linked badges
UPDATE badges b SET reward_gold = c.reward_gold
FROM challenges c WHERE c.reward_badge_id = b.id AND c.reward_gold IS NOT NULL AND c.reward_gold > 0;

-- 5) Drop challenge tables
DROP TABLE IF EXISTS user_challenge_progress;
DROP TABLE IF EXISTS challenges;
