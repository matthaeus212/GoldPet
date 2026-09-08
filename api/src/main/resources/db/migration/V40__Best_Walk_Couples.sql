CREATE TABLE best_walk_couples (
    id BIGSERIAL PRIMARY KEY,
    year_month VARCHAR(7) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    pet_id BIGINT NOT NULL REFERENCES pets(id),
    selected_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(year_month)
);

CREATE INDEX idx_best_walk_couples_user ON best_walk_couples(user_id);
CREATE INDEX idx_best_walk_couples_pet ON best_walk_couples(pet_id);
