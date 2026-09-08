-- V72: Walk Streak (W2a) — WALK-only 연속 산책 스트릭 (plan §2.2)
-- LOGIN 스트릭은 CUT (User.lastLogin 부재). 신뢰 가능한 산책 이벤트만 사용.
-- "하루" 경계는 애플리케이션에서 ZoneId.of("Asia/Seoul")로 명시 계산 → last_active_date 는 KST DATE.
-- freeze: 주 1회(ISO week) 무료. 주간 교체 마커는 last_active_date 의 ISO week 에서 파생(별도 컬럼 불필요).

CREATE TABLE IF NOT EXISTS user_streaks (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    current_streak   INT NOT NULL DEFAULT 0,
    longest_streak   INT NOT NULL DEFAULT 0,
    last_active_date DATE,
    freeze_count     INT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_streaks_last_active ON user_streaks(last_active_date);

COMMENT ON TABLE  user_streaks                  IS 'WALK 연속 산책 스트릭 (V72 — W2a). lazy-create, 과거 backfill 없음';
COMMENT ON COLUMN user_streaks.current_streak   IS '현재 연속 일수 (KST 기준). 갭 발생+프리즈 없음 시 1로 리셋';
COMMENT ON COLUMN user_streaks.longest_streak   IS '최장 연속 일수 기록';
COMMENT ON COLUMN user_streaks.last_active_date IS '마지막 적격 산책 날짜 (KST DATE). 같은날=무변화, 어제=+1, 그 이전=프리즈 차감 후 판정';
COMMENT ON COLUMN user_streaks.freeze_count     IS '보유 프리즈 수 (주 1회 무료, ISO week 교체 시 1로 재충전)';
