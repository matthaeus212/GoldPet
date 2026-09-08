CREATE TABLE IF NOT EXISTS consent_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    consent_type VARCHAR(30) NOT NULL,
    consent_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    is_agreed BOOLEAN NOT NULL,
    agreed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(45),
    CONSTRAINT fk_consent_history_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_consent_history_user_id ON consent_history(user_id);
CREATE INDEX IF NOT EXISTS idx_consent_history_type ON consent_history(consent_type);
