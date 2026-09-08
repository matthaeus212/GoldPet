ALTER TABLE places ADD COLUMN deleted_at TIMESTAMP;
CREATE INDEX idx_places_active ON places(deleted_at) WHERE deleted_at IS NULL;
