CREATE TABLE chat_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id),
    target_user_id BIGINT NOT NULL REFERENCES users(id),
    chat_room_id BIGINT REFERENCES chat_rooms(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMP,
    UNIQUE(requester_id, target_user_id)
);
CREATE INDEX idx_chat_requests_target ON chat_requests(target_user_id, status);
CREATE INDEX idx_chat_requests_requester ON chat_requests(requester_id, status);
