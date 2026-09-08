CREATE TABLE IF NOT EXISTS app_notices (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    image_url VARCHAR(500),
    link_url VARCHAR(500),
    target_screen VARCHAR(100),
    priority INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_dismissible BOOLEAN NOT NULL DEFAULT TRUE,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP,
    created_by BIGINT REFERENCES admin_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_notices_active ON app_notices(is_active, start_at, end_at);
CREATE INDEX idx_app_notices_type ON app_notices(type);
