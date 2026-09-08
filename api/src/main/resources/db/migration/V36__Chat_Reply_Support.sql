-- V36: Add reply support to chat messages
ALTER TABLE chat_messages ADD COLUMN reply_to_id BIGINT REFERENCES chat_messages(id);
CREATE INDEX idx_chat_messages_reply_to ON chat_messages(reply_to_id);
