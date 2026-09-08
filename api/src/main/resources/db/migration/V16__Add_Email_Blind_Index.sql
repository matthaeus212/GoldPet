-- Add email_hash column for blind index search
ALTER TABLE users ADD COLUMN email_hash VARCHAR(255);
CREATE INDEX idx_users_email_hash ON users(email_hash);
