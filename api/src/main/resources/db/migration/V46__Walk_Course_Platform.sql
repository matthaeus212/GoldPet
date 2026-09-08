-- walk_courses
CREATE TABLE walk_courses (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(100) NOT NULL,
    description TEXT,
    path geometry(LineString, 4326) NOT NULL,
    distance_km DOUBLE PRECISION NOT NULL,
    estimated_minutes INT NOT NULL,
    difficulty VARCHAR(20) NOT NULL DEFAULT 'MODERATE',
    region VARCHAR(100) NOT NULL,
    start_location geometry(Point, 4326) NOT NULL,
    start_address VARCHAR(255),
    end_address VARCHAR(255),
    thumbnail_url VARCHAR(500),
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    walk_count INT NOT NULL DEFAULT 0,
    rating DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_count INT NOT NULL DEFAULT 0,
    is_published BOOLEAN NOT NULL DEFAULT true,
    origin_walk_id BIGINT REFERENCES walks(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_walk_courses_start_location ON walk_courses USING GIST (start_location);
CREATE INDEX idx_walk_courses_region ON walk_courses (region);
CREATE INDEX idx_walk_courses_author_id ON walk_courses (author_id);
CREATE INDEX idx_walk_courses_difficulty ON walk_courses (difficulty);

-- course_spots
CREATE TABLE course_spots (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES walk_courses(id) ON DELETE CASCADE,
    location geometry(Point, 4326) NOT NULL,
    type VARCHAR(30) NOT NULL,
    name VARCHAR(100),
    description TEXT,
    image_url VARCHAR(500),
    order_index INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_course_spots_course_id ON course_spots (course_id);
CREATE INDEX idx_course_spots_location ON course_spots USING GIST (location);

-- course_likes
CREATE TABLE course_likes (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES walk_courses(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(course_id, user_id)
);

-- course_comments
CREATE TABLE course_comments (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES walk_courses(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    parent_comment_id BIGINT REFERENCES course_comments(id),
    content TEXT NOT NULL,
    rating INT CHECK (rating >= 1 AND rating <= 5),
    like_count INT NOT NULL DEFAULT 0,
    is_hidden BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_course_comments_course_id ON course_comments (course_id);

-- course_comment_likes
CREATE TABLE course_comment_likes (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL REFERENCES course_comments(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(comment_id, user_id)
);

-- Walk 테이블에 followed_course_id 추가 (ON DELETE SET NULL)
ALTER TABLE walks ADD COLUMN followed_course_id BIGINT REFERENCES walk_courses(id) ON DELETE SET NULL;
