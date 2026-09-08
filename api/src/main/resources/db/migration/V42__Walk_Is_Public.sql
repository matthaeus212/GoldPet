-- Add public/private visibility flag to walks
ALTER TABLE walks ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT TRUE;

-- Partial composite index for community feed queries (ORDER BY start_time DESC WHERE is_public = true)
CREATE INDEX idx_walks_public_start_time ON walks(start_time DESC) WHERE is_public = true;
