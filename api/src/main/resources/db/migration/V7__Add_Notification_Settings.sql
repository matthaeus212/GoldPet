ALTER TABLE users ADD COLUMN is_chat_alert_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN is_community_alert_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN is_marketing_alert_enabled BOOLEAN NOT NULL DEFAULT false;
