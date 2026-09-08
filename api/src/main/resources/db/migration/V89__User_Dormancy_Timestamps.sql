-- 휴면계정 기능(STYLE-001): 마지막 로그인 시각과 휴면 전환 시각 기록
--
-- 기존에는 UserStatus.DORMANT enum 과 로그인 차단만 있고, 전환 기준이 될 타임스탬프도
-- 전환 배치도 해제 API 도 없었다(실제로 휴면 사용자 0명, 될 방법도 없었음).
--
-- CONCURRENTLY 는 쓰지 않는다 — Flyway 가 스키마 이력 트랜잭션을 잡은 채로 대기해 자기 자신과
-- 교착한다(V87 에서 라이브 장애 발생).

ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS dormant_at TIMESTAMP;

-- 기존 사용자 백필: 마지막 활동 시각을 알 수 없으므로 updated_at(없으면 created_at)으로 대체한다.
-- 이 값이 없으면 다음 배치에서 전원이 즉시 휴면 전환돼 로그인이 막힌다.
UPDATE users
   SET last_login_at = COALESCE(updated_at, created_at)
 WHERE last_login_at IS NULL;

-- 휴면 전환 배치가 스캔하는 조건(status + last_login_at) 인덱스.
CREATE INDEX IF NOT EXISTS idx_users_status_last_login_at
    ON users (status, last_login_at);
