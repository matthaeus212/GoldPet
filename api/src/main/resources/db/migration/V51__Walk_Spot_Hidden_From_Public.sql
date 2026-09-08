ALTER TABLE walk_spots ADD COLUMN hidden_from_public boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_walk_spots_hidden_from_public ON walk_spots (hidden_from_public);
