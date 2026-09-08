-- GoldPet Initial Schema
-- Matches JPA entity definitions as of 2026-02-10
-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- =================================================================================================
-- 1. Users Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    oauth_provider VARCHAR(255),
    oauth_id VARCHAR(255),
    username VARCHAR(255),
    password VARCHAR(255),
    nickname VARCHAR(255),
    name VARCHAR(255),
    birth_date DATE,
    phone_number VARCHAR(255),
    gender VARCHAR(255),
    birth_year INT,
    main_location_text VARCHAR(255),
    main_location_geom geometry(Point, 4326),
    profile_image_url VARCHAR(255),
    gold_balance INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    mbti VARCHAR(255),
    intro TEXT,
    has_pet BOOLEAN NOT NULL DEFAULT FALSE,
    is_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_main_location_geom ON users USING GIST (main_location_geom);

CREATE TABLE IF NOT EXISTS user_profile_images (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    image_url VARCHAR(255) NOT NULL,
    order_index INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interests (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    order_index INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS hobbies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    order_index INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_interests (
    user_id BIGINT NOT NULL REFERENCES users(id),
    interest_id BIGINT NOT NULL REFERENCES interests(id),
    PRIMARY KEY (user_id, interest_id)
);

CREATE TABLE IF NOT EXISTS user_hobbies (
    user_id BIGINT NOT NULL REFERENCES users(id),
    hobby_id BIGINT NOT NULL REFERENCES hobbies(id),
    PRIMARY KEY (user_id, hobby_id)
);

-- =================================================================================================
-- 2. Pet Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS pet_species (
    id SERIAL PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pet_breeds (
    id SERIAL PRIMARY KEY,
    species_id INT NOT NULL REFERENCES pet_species(id),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    category VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pet_breeds_species_id ON pet_breeds(species_id);

CREATE TABLE IF NOT EXISTS pets (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    species_id INT NOT NULL REFERENCES pet_species(id),
    breed_id INT REFERENCES pet_breeds(id),
    gender VARCHAR(255),
    birth_date DATE,
    weight_kg DOUBLE PRECISION,
    is_neutered BOOLEAN,
    profile_image_url VARCHAR(255),
    temperament_tags TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pets_owner_user_id ON pets(owner_user_id);

CREATE TABLE IF NOT EXISTS pet_attributes (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(255) NOT NULL,
    code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    input_type VARCHAR(255) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    options JSONB
);

CREATE TABLE IF NOT EXISTS pet_profile_images (
    id BIGSERIAL PRIMARY KEY,
    pet_id BIGINT NOT NULL REFERENCES pets(id),
    image_url VARCHAR(255) NOT NULL,
    order_index INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================================
-- 3. Walk Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS walks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    distance_km DOUBLE PRECISION NOT NULL,
    duration_seconds BIGINT NOT NULL,
    path geometry(LineString, 4326) NOT NULL,
    calories_burned DOUBLE PRECISION,
    notes VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_walks_user_id ON walks(user_id);
CREATE INDEX IF NOT EXISTS idx_walks_path ON walks USING GIST (path);

CREATE TABLE IF NOT EXISTS walk_spots (
    id BIGSERIAL PRIMARY KEY,
    walk_id BIGINT NOT NULL REFERENCES walks(id),
    location geometry(Point, 4326) NOT NULL,
    type VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    image_url VARCHAR(255),
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_walk_spots_geom ON walk_spots USING GIST (location);

-- =================================================================================================
-- 4. Social Domain (Likes, Matches, Blocks)
-- =================================================================================================

CREATE TABLE IF NOT EXISTS likes (
    id BIGSERIAL PRIMARY KEY,
    from_user_id BIGINT NOT NULL REFERENCES users(id),
    to_user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (from_user_id, to_user_id)
);

CREATE TABLE IF NOT EXISTS matches (
    id BIGSERIAL PRIMARY KEY,
    user1_id BIGINT NOT NULL REFERENCES users(id),
    user2_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user1_id, user2_id)
);

CREATE TABLE IF NOT EXISTS user_blocks (
    id BIGSERIAL PRIMARY KEY,
    blocker_id BIGINT NOT NULL REFERENCES users(id),
    blocked_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (blocker_id, blocked_id)
);

-- =================================================================================================
-- 5. Chat Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS chat_rooms (
    id BIGSERIAL PRIMARY KEY,
    room_type VARCHAR(255) NOT NULL,
    title VARCHAR(255),
    owner_user_id BIGINT REFERENCES users(id),
    match_id BIGINT REFERENCES matches(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_room_participants (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES chat_rooms(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(255) NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP,
    last_read_at TIMESTAMP,
    UNIQUE (room_id, user_id)
);

CREATE TABLE IF NOT EXISTS file_attachments (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    file_type VARCHAR(255) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    url VARCHAR(255) NOT NULL,
    size_bytes BIGINT,
    width INT,
    height INT,
    duration_sec INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Emoticon tables (no JPA entity but used by chat_messages FK and seed data)
CREATE TABLE IF NOT EXISTS emoticon_packs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price_gold INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS emoticons (
    id BIGSERIAL PRIMARY KEY,
    pack_id BIGINT NOT NULL REFERENCES emoticon_packs(id),
    code VARCHAR(50),
    image_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES chat_rooms(id),
    sender_user_id BIGINT REFERENCES users(id),
    message_type VARCHAR(255) NOT NULL,
    text_content TEXT,
    file_id BIGINT,
    emoticon_id BIGINT,
    emoji_code VARCHAR(255),
    read_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_room_created ON chat_messages(room_id, created_at);

-- =================================================================================================
-- 6. Community Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS community_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS community_posts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    category_id BIGINT NOT NULL REFERENCES community_categories(id),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    visibility VARCHAR(255) NOT NULL DEFAULT 'PUBLIC',
    view_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    post_type VARCHAR(255) NOT NULL DEFAULT 'GENERAL',
    adopted_comment_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS community_post_images (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES community_posts(id),
    file_id BIGINT NOT NULL REFERENCES file_attachments(id),
    sort_order INT NOT NULL
);

CREATE TABLE IF NOT EXISTS community_comments (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES community_posts(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    parent_comment_id BIGINT REFERENCES community_comments(id),
    content TEXT NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add FK for adopted_comment_id after community_comments exists
ALTER TABLE community_posts ADD CONSTRAINT fk_community_posts_adopted_comment
    FOREIGN KEY (adopted_comment_id) REFERENCES community_comments(id);

CREATE TABLE IF NOT EXISTS community_post_likes (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES community_posts(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS community_comment_likes (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL REFERENCES community_comments(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (comment_id, user_id)
);

-- =================================================================================================
-- 7. Gold / Economy Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS gold_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(255) NOT NULL,
    amount INT NOT NULL,
    balance_after INT NOT NULL,
    description VARCHAR(255),
    reference_type VARCHAR(255),
    reference_id BIGINT,
    status VARCHAR(255) NOT NULL DEFAULT 'COMPLETED',
    payment_method VARCHAR(255),
    external_transaction_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================================
-- 8. Notification Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(255) NOT NULL,
    target_id BIGINT,
    target_type VARCHAR(255),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    sender_id BIGINT,
    sender_nickname VARCHAR(255),
    sender_profile_image VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================================
-- 9. Gamification Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS badges (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    image_url VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_badges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    badge_id BIGINT NOT NULL REFERENCES badges(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, badge_id)
);

CREATE TABLE IF NOT EXISTS challenges (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    goal_type VARCHAR(255) NOT NULL,
    goal_value INT NOT NULL,
    reward_gold INT,
    reward_badge_id BIGINT REFERENCES badges(id),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS user_challenge_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    challenge_id BIGINT NOT NULL REFERENCES challenges(id),
    current_value INT NOT NULL DEFAULT 0,
    status VARCHAR(255) NOT NULL DEFAULT 'IN_PROGRESS',
    completed_at TIMESTAMP,
    UNIQUE (user_id, challenge_id)
);

-- =================================================================================================
-- 10. Places & Check-in Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS places (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    location_geom geometry(Point, 4326) NOT NULL,
    description VARCHAR(255),
    image_url VARCHAR(255),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    checkin_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_places_geom ON places USING GIST (location_geom);

CREATE TABLE IF NOT EXISTS check_ins (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    place_id BIGINT NOT NULL REFERENCES places(id),
    photo_url VARCHAR(255),
    memo VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================================
-- 11. Common Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(255) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================================
-- 12. Report Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    target_id BIGINT NOT NULL,
    target_preview VARCHAR(255),
    reason VARCHAR(255) NOT NULL,
    reporter_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    resolved_at TIMESTAMP,
    resolved_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================================
-- 13. Admin Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS admin_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL DEFAULT 'OPERATOR',
    otp_secret VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_access_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL REFERENCES admin_users(id),
    ip_address VARCHAR(50),
    action VARCHAR(100),
    target_resource VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS banners (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    image_url VARCHAR(255),
    link VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    display_order INT DEFAULT 0,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'DRAFT',
    participant_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    title_template VARCHAR(200) NOT NULL,
    message_template VARCHAR(1000) NOT NULL,
    type VARCHAR(50) NOT NULL,
    variables TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS push_delivery_logs (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT REFERENCES notification_templates(id),
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_value VARCHAR(500),
    target_count INT NOT NULL DEFAULT 0,
    delivered_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    scheduled_at TIMESTAMP,
    sent_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    sent_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cache_clear_logs (
    id BIGSERIAL PRIMARY KEY,
    cache_name VARCHAR(100) NOT NULL,
    cleared_by VARCHAR(100) NOT NULL,
    cleared_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255)
);

-- =================================================================================================
-- 14. AI Profile Domain
-- =================================================================================================

CREATE TABLE IF NOT EXISTS ai_styles (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    preview_url VARCHAR(500),
    gold_cost INT NOT NULL DEFAULT 10,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_profile_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    pet_id BIGINT NOT NULL REFERENCES pets(id),
    type VARCHAR(255) NOT NULL,
    source_image_url VARCHAR(255) NOT NULL,
    style_prompt VARCHAR(255),
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    result_url VARCHAR(255),
    error_message VARCHAR(255),
    gold_cost INT NOT NULL DEFAULT 10,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
