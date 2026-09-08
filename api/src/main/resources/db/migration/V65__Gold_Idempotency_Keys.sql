-- V65: Gold transaction idempotency keys
-- Pre-launch BLOCKER §1-6 (a) — Idempotency-Key 헤더 기반 골드 트랜잭션 무결성 보장.
-- Pattern: INSERT ... ON CONFLICT DO NOTHING (Postgres) + status 상태 머신.
-- TTL: 7일. 일별 cleanup job (Quartz GoldIdempotencyCleanupJob).

CREATE TABLE IF NOT EXISTS gold_idempotency_keys (
    idempotency_key   VARCHAR(64) PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    request_hash      VARCHAR(128) NOT NULL,
    transaction_id    BIGINT,
    response_body     JSONB,
    status            VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    http_status       SMALLINT,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    completed_at      TIMESTAMP,
    expires_at        TIMESTAMP NOT NULL DEFAULT (now() + INTERVAL '7 days'),
    CONSTRAINT chk_gold_idem_status CHECK (status IN ('PROCESSING','COMPLETED','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_gold_idem_expires ON gold_idempotency_keys(expires_at);
CREATE INDEX IF NOT EXISTS idx_gold_idem_user_status ON gold_idempotency_keys(user_id, status);

COMMENT ON TABLE gold_idempotency_keys IS '골드 트랜잭션 idempotency 보장 (7d TTL, daily cleanup)';
COMMENT ON COLUMN gold_idempotency_keys.idempotency_key IS '클라이언트 제공 Idempotency-Key 헤더 (uuid 권장)';
COMMENT ON COLUMN gold_idempotency_keys.request_hash IS 'SHA-256(userId + endpoint + body) — 동일 key + 다른 hash는 422 misuse';
COMMENT ON COLUMN gold_idempotency_keys.status IS 'PROCESSING | COMPLETED | FAILED';
COMMENT ON COLUMN gold_idempotency_keys.response_body IS '완료된 트랜잭션의 응답 JSON — 동일 key 재요청 시 그대로 반환';
