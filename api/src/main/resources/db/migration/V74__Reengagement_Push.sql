-- V74: 재참여 푸시 (W2c, plan §2.4)
-- 동의: WALK 의 else->true 상시발송 레인 미사용. 신규 전용 카테고리 컬럼.
-- 멱등: 휴면 유저당 일일 dedup (reengage:{userId}:{date}) → 배포 재시작 중복 발송 차단, 하루 1회 캡.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_reengagement_alert_enabled BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN users.is_reengagement_alert_enabled IS '재참여 푸시(휴면/스트릭-위기 넛지) 동의 — WALK 상시 레인과 분리(V74, W2c)';

CREATE TABLE IF NOT EXISTS reengagement_sends (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    send_date   DATE NOT NULL,
    nudge_type  VARCHAR(32) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_reengagement_user_date UNIQUE (user_id, send_date)
);

CREATE INDEX IF NOT EXISTS idx_reengagement_send_date ON reengagement_sends(send_date);

COMMENT ON TABLE  reengagement_sends            IS '재참여 넛지 일일 dedup (하루 1회 캡, 배포 재시작 멱등) — V74 W2c';
COMMENT ON COLUMN reengagement_sends.send_date  IS 'KST 발송 날짜. (user_id, send_date) UNIQUE = 하루 1회 캡';
COMMENT ON COLUMN reengagement_sends.nudge_type IS 'DORMANCY | STREAK_AT_RISK';
