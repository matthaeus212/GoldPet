-- Add start/end location columns to walks
ALTER TABLE walks ADD COLUMN start_address VARCHAR(255);
ALTER TABLE walks ADD COLUMN start_location geometry(Point, 4326);
ALTER TABLE walks ADD COLUMN end_location geometry(Point, 4326);

-- Spatial index for future "walks near me" queries
CREATE INDEX IF NOT EXISTS idx_walks_start_location ON walks USING GIST (start_location);

-- Walk-Pet join table (with audit columns for BaseTimeEntity)
CREATE TABLE IF NOT EXISTS walk_pets (
    walk_id BIGINT NOT NULL REFERENCES walks(id) ON DELETE CASCADE,
    pet_id BIGINT NOT NULL REFERENCES pets(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (walk_id, pet_id)
);

CREATE INDEX IF NOT EXISTS idx_walk_pets_pet_id ON walk_pets(pet_id);
