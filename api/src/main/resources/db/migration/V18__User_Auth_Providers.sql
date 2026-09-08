-- Multi-provider identity table
CREATE TABLE IF NOT EXISTS user_auth_providers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (provider, provider_id)
);

CREATE INDEX idx_user_auth_providers_user_id ON user_auth_providers(user_id);

-- Migrate existing oauth data from users table (skip duplicates)
INSERT INTO user_auth_providers (user_id, provider, provider_id, is_primary, linked_at)
SELECT DISTINCT ON (oauth_provider, oauth_id) id, oauth_provider, oauth_id, TRUE, created_at
FROM users
WHERE oauth_provider IS NOT NULL
  AND oauth_provider != ''
  AND oauth_id IS NOT NULL
  AND oauth_id != ''
ORDER BY oauth_provider, oauth_id, created_at ASC;
