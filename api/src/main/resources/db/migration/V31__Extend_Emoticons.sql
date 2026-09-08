-- Extend existing emoticons table (created in V1__Initial_Schema.sql)
ALTER TABLE emoticons ADD COLUMN IF NOT EXISTS name VARCHAR(100);
ALTER TABLE emoticons ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE emoticons ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE emoticons ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT now();

-- Extend existing emoticon_packs table (created in V1__Initial_Schema.sql)
ALTER TABLE emoticon_packs ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE emoticon_packs ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT now();

-- Indexes for public API query
CREATE INDEX IF NOT EXISTS idx_emoticons_active_sort ON emoticons (is_active, sort_order);
CREATE INDEX IF NOT EXISTS idx_emoticon_packs_active_sort ON emoticon_packs (is_active, sort_order);
