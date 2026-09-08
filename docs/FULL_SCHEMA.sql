-- PostGIS 확장 활성화
CREATE EXTENSION IF NOT EXISTS postgis;

-- 0. 공통 테이블
CREATE TABLE file_attachments (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL, -- REFERENCES users(id) but user table does not exist yet
    file_type VARCHAR(20) NOT NULL, -- 'IMAGE', 'VIDEO', 'DOCUMENT', ...
    mime_type VARCHAR(100) NOT NULL,
    url VARCHAR(500) NOT NULL,
    size_bytes BIGINT,
    width INT,
    height INT,
    duration_sec INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 1. 유저 & 펫 도메인
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    oauth_provider VARCHAR(50) NOT NULL,
    oauth_id VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    gender VARCHAR(10),
    birth_year INT,
    main_location_text VARCHAR(255),
    main_location_geom GEOMETRY(Point, 4326),
    profile_image_url VARCHAR(500),
    gold_balance INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (oauth_provider, oauth_id)
);
CREATE INDEX IF NOT EXISTS users_main_location_geom_idx ON users USING GIST (main_location_geom);

-- users 테이블 생성 후 file_attachments에 외래 키 제약 조건 추가
ALTER TABLE file_attachments ADD CONSTRAINT fk_file_attachments_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id);


CREATE TABLE user_location_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    location_geom GEOMETRY(Point, 4326) NOT NULL,
    accuracy_m FLOAT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS user_location_history_user_id_idx ON user_location_history(user_id);
CREATE INDEX IF NOT EXISTS user_location_history_location_geom_idx ON user_location_history USING GIST (location_geom);


CREATE TABLE pet_species (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pet_breeds (
    id SERIAL PRIMARY KEY,
    species_id INT NOT NULL REFERENCES pet_species(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS pet_breeds_species_id_idx ON pet_breeds(species_id);


CREATE TABLE pets (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(50) NOT NULL,
    species_id INT NOT NULL REFERENCES pet_species(id),
    breed_id INT REFERENCES pet_breeds(id),
    gender VARCHAR(10),
    birth_date DATE,
    weight_kg DECIMAL(4,1),
    is_neutered BOOLEAN,
    profile_image_url VARCHAR(500),
    temperament_tags VARCHAR(255), -- Comma-separated tags
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS pets_owner_user_id_idx ON pets(owner_user_id);
CREATE INDEX IF NOT EXISTS pets_species_breed_id_idx ON pets(species_id, breed_id);


CREATE TABLE pet_attribute_defs (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    species_id INT REFERENCES pet_species(id),
    data_type VARCHAR(20) NOT NULL, -- 'STRING', 'NUMBER', 'BOOLEAN', 'ENUM'
    enum_values TEXT, -- JSON or comma-separated
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pet_attribute_values (
    id BIGSERIAL PRIMARY KEY,
    pet_id BIGINT NOT NULL REFERENCES pets(id),
    attribute_def_id INT NOT NULL REFERENCES pet_attribute_defs(id),
    value_string TEXT,
    value_number DECIMAL(10,2),
    value_boolean BOOLEAN,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(pet_id, attribute_def_id)
);

-- 2. 산책 & 위치(지도) 도메인
CREATE TABLE walk_albums (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(100) NOT NULL,
    description TEXT,
    cover_walk_id BIGINT, -- Cannot add FK yet
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE', -- PUBLIC / FRIENDS / PRIVATE
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE walk_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    pet_id BIGINT NOT NULL REFERENCES pets(id),
    album_id BIGINT REFERENCES walk_albums(id),
    title VARCHAR(100) NOT NULL,
    distance_m INT NOT NULL,
    duration_sec INT NOT NULL,
    user_calorie INT,
    pet_calorie INT,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NOT NULL,
    route_geom GEOMETRY(LineString, 4326),
    center_point GEOMETRY(Point, 4326),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE', -- PUBLIC / FRIENDS / PRIVATE
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS walk_records_user_id_idx ON walk_records(user_id);
CREATE INDEX IF NOT EXISTS walk_records_route_geom_idx ON walk_records USING GIST (route_geom);
CREATE INDEX IF NOT EXISTS walk_records_center_point_idx ON walk_records USING GIST (center_point);

-- Add FK constraint to walk_albums after walk_records is created
ALTER TABLE walk_albums ADD CONSTRAINT fk_walk_albums_cover_walk_id FOREIGN KEY (cover_walk_id) REFERENCES walk_records(id);

CREATE TABLE walk_spots (
    id BIGSERIAL PRIMARY KEY,
    walk_id BIGINT NOT NULL REFERENCES walk_records(id),
    location_geom GEOMETRY(Point, 4326) NOT NULL,
    photo_url VARCHAR(500),
    memo TEXT,
    taken_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS walk_spots_walk_id_idx ON walk_spots(walk_id);
CREATE INDEX IF NOT EXISTS walk_spots_location_geom_idx ON walk_spots USING GIST (location_geom);


-- 3. 매칭 & 친구 도메인
CREATE TABLE likes (
    id BIGSERIAL PRIMARY KEY,
    from_user_id BIGINT NOT NULL REFERENCES users(id),
    to_user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / CANCELED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (from_user_id, to_user_id)
);

CREATE TABLE matches (
    id BIGSERIAL PRIMARY KEY,
    user1_id BIGINT NOT NULL REFERENCES users(id),
    user2_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'MATCHED', -- MATCHED / BLOCKED / ENDED
    last_action_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user1_id, user2_id),
    CHECK (user1_id < user2_id)
);

-- 4. 채팅 도메인
CREATE TABLE chat_rooms (
    id BIGSERIAL PRIMARY KEY,
    room_type VARCHAR(20) NOT NULL, -- 'DIRECT', 'GROUP', 'AI_PET'
    title VARCHAR(100),
    owner_user_id BIGINT REFERENCES users(id),
    match_id BIGINT REFERENCES matches(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_room_participants (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES chat_rooms(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER', -- 'OWNER', 'MEMBER'
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP,
    UNIQUE (room_id, user_id)
);

CREATE TABLE emoticon_packs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price_gold INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE emoticons (
    id BIGSERIAL PRIMARY KEY,
    pack_id BIGINT NOT NULL REFERENCES emoticon_packs(id),
    code VARCHAR(50),
    image_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES chat_rooms(id),
    sender_user_id BIGINT REFERENCES users(id), -- System messages can have NULL sender
    message_type VARCHAR(20) NOT NULL, -- 'TEXT', 'IMAGE', 'VIDEO', 'FILE', 'EMOTICON', 'EMOJI', 'SYSTEM'
    text_content TEXT,
    file_id BIGINT REFERENCES file_attachments(id),
    emoticon_id BIGINT REFERENCES emoticons(id),
    emoji_code VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_count INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS chat_messages_room_id_created_at_idx ON chat_messages(room_id, created_at);


-- 5. 커뮤니티 도메인
CREATE TABLE community_categories (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE community_posts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    category_id INT NOT NULL REFERENCES community_categories(id),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', -- PUBLIC / FRIENDS
    view_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    post_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL', -- GENERAL, QUESTION, INFO
    adopted_comment_id BIGINT, -- Cannot add FK yet
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE community_comments (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES community_posts(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    parent_comment_id BIGINT REFERENCES community_comments(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE community_posts ADD CONSTRAINT fk_community_posts_adopted_comment_id FOREIGN KEY (adopted_comment_id) REFERENCES community_comments(id);


CREATE TABLE community_post_images (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES community_posts(id),
    file_id BIGINT NOT NULL REFERENCES file_attachments(id),
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE community_post_likes (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES community_posts(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (post_id, user_id)
);

-- 6. Gold & 과금 도메인
CREATE TABLE gold_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(20) NOT NULL, -- 'CHARGE', 'USE', 'REFUND'
    amount INT NOT NULL,
    balance_after INT NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    reference_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE iap_receipts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    platform VARCHAR(20) NOT NULL, -- 'GOOGLE', 'APPLE'
    receipt_data TEXT NOT NULL,
    order_id VARCHAR(100) UNIQUE NOT NULL,
    purchased_gold INT NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP
);

-- 7. AI & 기타 도메인
CREATE TABLE ai_profile_jobs (
    id BIGSERIAL PRIMARY KEY,
    pet_id BIGINT NOT NULL REFERENCES pets(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    style VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL, -- 'REQUESTED', 'PROCESSING', 'DONE', 'FAILED'
    input_image_id BIGINT REFERENCES file_attachments(id),
    result_image_id BIGINT REFERENCES file_attachments(id),
    result_video_id BIGINT REFERENCES file_attachments(id),
    used_gold INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL, -- 'MATCH', 'LIKE', 'NEW_COMMENT', 'CHALLENGE_COMPLETE', 'BADGE_EARNED'
    title VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    payload_json JSONB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP
);

-- 8. 게이미피케이션 & 장소 (v2.1 신규)
CREATE TABLE badges (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    image_url VARCHAR(500) NOT NULL
);

CREATE TABLE user_badges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    badge_id INT NOT NULL REFERENCES badges(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, badge_id)
);

CREATE TABLE challenges (
    id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    goal_type VARCHAR(50) NOT NULL, -- e.g., 'WALK_DISTANCE_TOTAL', 'CHECK_IN_COUNT'
    goal_value INT NOT NULL,
    reward_gold INT,
    reward_badge_id INT REFERENCES badges(id),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE user_challenge_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    challenge_id INT NOT NULL REFERENCES challenges(id),
    current_value INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, FAILED
    completed_at TIMESTAMP,
    UNIQUE (user_id, challenge_id)
);

CREATE TABLE places (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50), -- e.g., 'CAFE', 'PARK', 'RESTAURANT'
    address VARCHAR(255),
    location_geom GEOMETRY(Point, 4326) NOT NULL
);
CREATE INDEX IF NOT EXISTS places_location_geom_idx ON places USING GIST (location_geom);


CREATE TABLE check_ins (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    place_id BIGINT NOT NULL REFERENCES places(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS check_ins_user_place_idx ON check_ins(user_id, place_id);
-- 9. 관리자 도메인 (Admin)
CREATE TABLE admin_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'OPERATOR', -- 'SUPER_ADMIN', 'OPERATOR', 'VIEWER'
    otp_secret VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_access_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL REFERENCES admin_users(id),
    ip_address VARCHAR(50),
    action VARCHAR(100), -- 'LOGIN', 'LOGOUT', ...
    target_resource VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 초기 슈퍼 관리자 계정 (비밀번호: admin123 -> BCrypt 해시 필요, 임시로 plain text로 넣지 않고 나중에 코드에서 처리하거나 해시값 넣음)
-- $2a$10$8K1p/a0dL.0O.n.9U.6/6.0.0.0.0.0.0.0.0.0.0.0 (예시)
