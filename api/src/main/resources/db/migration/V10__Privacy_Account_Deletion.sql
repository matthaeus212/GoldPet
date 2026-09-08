-- Deleted users tracking table for PIPA compliance
CREATE TABLE IF NOT EXISTS deleted_users (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reason TEXT,
    deleted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    data_deletion_scheduled_at TIMESTAMP NOT NULL DEFAULT (NOW() + INTERVAL '30 days'),
    deleted_by VARCHAR(20) NOT NULL DEFAULT 'SELF',
    CONSTRAINT fk_deleted_users_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_deleted_users_user_id ON deleted_users(user_id);
CREATE INDEX idx_deleted_users_deleted_at ON deleted_users(deleted_at);

-- Add privacy settings columns to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_location_sharing_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_profile_public BOOLEAN NOT NULL DEFAULT true;
