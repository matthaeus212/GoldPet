-- V79: 코호트 write-once 영속 (W1(1), plan §1 — A/B 무결성 핵심, M2 해결).
-- FriendService 첫 노출 시 1회 버킷팅 → 이 행에서 serving + like_events 둘 다 cohort READ(재계산 금지).
-- splitPct/salt 런타임 변경은 미할당 신규 유저에만 적용 → 기존 유저 retroactive 재버킷 불가(어드민 splitPct 변조 구조적 무력화).
-- UNIQUE(user_id, experiment_key) = INSERT ON CONFLICT DO NOTHING 대상(동시 첫노출 레이스 → 동일 코호트 + 1행).
-- 행동/실험 데이터 → ON DELETE CASCADE (privacy).

CREATE TABLE IF NOT EXISTS experiment_assignment (
    id                      BIGSERIAL   PRIMARY KEY,
    user_id                 BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    experiment_key          VARCHAR(64) NOT NULL,
    cohort                  VARCHAR(16) NOT NULL CHECK (cohort IN ('TREATMENT', 'CONTROL')),
    split_pct_at_assignment INT         NOT NULL,
    salt_version            INT         NOT NULL,
    assigned_at             TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_experiment_assignment_user_key UNIQUE (user_id, experiment_key)
);

-- 코호트별 window 집계(match-rate A/B 엔드포인트, W2) 가속.
CREATE INDEX IF NOT EXISTS idx_experiment_assignment_key_cohort
    ON experiment_assignment (experiment_key, cohort);

COMMENT ON TABLE  experiment_assignment                         IS 'write-once A/B 코호트 영속 — 노출 시점 1회 버킷팅, serving+like_events 둘 다 read (V79, W1)';
COMMENT ON COLUMN experiment_assignment.cohort                  IS 'TREATMENT | CONTROL — 할당 후 불변(재계산 금지)';
COMMENT ON COLUMN experiment_assignment.split_pct_at_assignment IS '할당 당시 split_pct 스냅샷(이후 어드민이 바꿔도 이 행은 불변)';
COMMENT ON COLUMN experiment_assignment.salt_version            IS '할당 당시 salt 버전 스냅샷';
