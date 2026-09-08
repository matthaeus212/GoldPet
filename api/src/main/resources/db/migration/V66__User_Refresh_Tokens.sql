-- V66: JWT refresh token rotation + reuse detection (Sprint 1 BLOCKER §1-1 #2)
-- 현재 JwtTokenProvider는 stateless validate만 수행 → DB revoke 불가.
-- V66은 refresh token chain을 SHA-256 해시로 DB에 기록하여
-- (1) rotation: 매 refresh 호출마다 new token 발급 + parent 즉시 revoke
-- (2) reuse detection: revoked token 재사용 시도 → 해당 device chain 전체 revoke (다른 device 무영향)
-- (3) grace window: 동일 device_id + 동일 parent_token_hash 5초 내 재호출은 멱등 (앱 백그라운드 복귀 race 보호)

CREATE TABLE IF NOT EXISTS user_refresh_tokens (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id         VARCHAR(255) NOT NULL,
    token_hash        VARCHAR(128) NOT NULL UNIQUE,
    parent_token_hash VARCHAR(128),
    issued_at         TIMESTAMP NOT NULL DEFAULT now(),
    expires_at        TIMESTAMP NOT NULL,
    revoked_at        TIMESTAMP,
    revoke_reason     VARCHAR(32),
    user_agent        VARCHAR(500),
    ip_address        VARCHAR(64),
    CONSTRAINT chk_user_refresh_reason CHECK (
        revoke_reason IS NULL OR revoke_reason IN ('ROTATED','REUSE_DETECTED','ADMIN_FORCE','LOGOUT','EXPIRED')
    )
);

CREATE INDEX IF NOT EXISTS idx_user_refresh_user_device ON user_refresh_tokens(user_id, device_id);
CREATE INDEX IF NOT EXISTS idx_user_refresh_parent      ON user_refresh_tokens(parent_token_hash) WHERE parent_token_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_user_refresh_expires     ON user_refresh_tokens(expires_at)        WHERE revoked_at IS NULL;

COMMENT ON TABLE  user_refresh_tokens                    IS 'JWT refresh token rotation chain (V66 — BLOCKER #2)';
COMMENT ON COLUMN user_refresh_tokens.token_hash         IS 'SHA-256 hex of refresh token (plain token never stored)';
COMMENT ON COLUMN user_refresh_tokens.parent_token_hash  IS '회전 이전 token_hash (initial issue 시 NULL)';
COMMENT ON COLUMN user_refresh_tokens.revoke_reason      IS 'ROTATED | REUSE_DETECTED | ADMIN_FORCE | LOGOUT | EXPIRED';
COMMENT ON COLUMN user_refresh_tokens.device_id          IS 'V17 user_devices.device_id 와 동일 형식 (255 char)';
