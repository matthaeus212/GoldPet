-- 1. New table: user_devices
CREATE TABLE IF NOT EXISTS user_devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id VARCHAR(255) NOT NULL,
    fcm_token VARCHAR(500),
    device_type VARCHAR(50) NOT NULL,
    device_name VARCHAR(255),
    app_version VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, device_id)
);

CREATE INDEX idx_user_devices_user_id ON user_devices(user_id);
CREATE INDEX idx_user_devices_fcm_token ON user_devices(fcm_token);

-- 2. Migrate existing fcm_token data
INSERT INTO user_devices (user_id, device_id, fcm_token, device_type, last_login_at, created_at, updated_at)
SELECT id, 'legacy-' || id, fcm_token, 'UNKNOWN', updated_at, NOW(), NOW()
FROM users WHERE fcm_token IS NOT NULL;

-- 3. phone_number_hash for unique constraint
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number_hash VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_number_hash
    ON users(phone_number_hash) WHERE phone_number_hash IS NOT NULL;

-- 4. profile_locked_at for profile immutability (Feature 3)
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_locked_at TIMESTAMP;
