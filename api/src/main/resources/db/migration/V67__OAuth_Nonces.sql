-- V67: OAuth flow nonce — replay 차단 (Sprint 2 BLOCKER #3).
-- 현재 SocialLoginService.confirmLink (line 406-457) 는 LINK_SUGGESTION JWT 가 5분 TTL 동안 무제한 재사용 가능.
-- V67 은 nonce_uuid 를 DB primary 로 관리하여 atomic 1회용 검증을 보장.
-- 사용 패턴:
--   issue: OAuth flow 시작 시 UUID 발급 + INSERT (expires_at = now + 5min)
--   consume: callback 시 atomic UPDATE used_at = now() WHERE nonce = ? AND used_at IS NULL AND expires_at > now()
--   결과 0건 → replay/expired/not-found → 401/400 거부

CREATE TABLE IF NOT EXISTS oauth_nonces (
    nonce_uuid  UUID PRIMARY KEY,
    user_id     BIGINT,
    provider    VARCHAR(32) NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_oauth_nonces_expires ON oauth_nonces(expires_at) WHERE used_at IS NULL;

COMMENT ON TABLE  oauth_nonces             IS 'OAuth flow nonce — replay 차단 (V67, BLOCKER #3)';
COMMENT ON COLUMN oauth_nonces.provider    IS 'LINK_SUGGESTION | LOGIN | SIGNUP — 어떤 OAuth 흐름의 nonce 인지';
COMMENT ON COLUMN oauth_nonces.user_id     IS '인증된 사용자에 한해 nullable 미설정 (e.g. LOGIN 시점에는 미사용)';
COMMENT ON COLUMN oauth_nonces.used_at     IS 'consume 시점. NULL 이면 미사용. atomic UPDATE 로 보장.';
