ALTER TABLE admin_users ADD COLUMN last_login_at TIMESTAMP;
ALTER TABLE admin_users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;
