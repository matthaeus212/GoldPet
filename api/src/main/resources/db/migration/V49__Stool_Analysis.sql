CREATE TABLE stool_analyses (
    id BIGSERIAL PRIMARY KEY,
    walk_spot_id BIGINT REFERENCES walk_spots(id),
    pet_id BIGINT NOT NULL REFERENCES pets(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    image_url VARCHAR(500) NOT NULL,
    color_score INT,
    consistency_score INT,
    coating_score INT,
    contents_score INT,
    overall_score INT,
    color_assessment VARCHAR(100),
    consistency_assessment VARCHAR(100),
    coating_assessment VARCHAR(100),
    contents_assessment VARCHAR(100),
    health_summary TEXT,
    health_tips JSONB,
    warnings JSONB,
    ai_raw_response TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    analyzed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE stool_analyses ADD CONSTRAINT uq_stool_analyses_walk_spot_id UNIQUE (walk_spot_id);
CREATE INDEX idx_stool_analyses_pet_id ON stool_analyses(pet_id);
CREATE INDEX idx_stool_analyses_user_id ON stool_analyses(user_id);
CREATE INDEX idx_stool_analyses_created_at ON stool_analyses(created_at DESC);
