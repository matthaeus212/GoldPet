-- V52: Walk Photo Variants
-- Adds viewer_url column to file_attachments and variant image key columns to walk_spots
-- for the walk-photo feed architecture (Phase 0).
--
-- Ordering rule: column adds MUST precede the unique index so that if the index
-- creation aborts (duplicate urls), the schema is not left half-migrated.
--
-- PRE-FLIGHT DUPLICATE AUDIT (run manually before applying to prod):
--   SELECT url, COUNT(*) FROM file_attachments GROUP BY url HAVING COUNT(*) > 1;
-- If duplicates exist: dedupe first, or use the non-unique fallback index (see runbook).

-- 1) Add viewer_url to file_attachments (nullable; backfilled by FileService variant pipeline)
ALTER TABLE file_attachments ADD COLUMN IF NOT EXISTS viewer_url VARCHAR(500);

-- 2) Add variant image key columns to walk_spots (nullable; backfilled asynchronously)
--    Do this BEFORE the unique index below so the scheduler / entities have their columns
--    regardless of whether the unique index creation succeeds.
ALTER TABLE walk_spots
    ADD COLUMN IF NOT EXISTS image_key_viewer VARCHAR(255),
    ADD COLUMN IF NOT EXISTS image_key_thumb  VARCHAR(255);

-- 3) Programmatic guard for the unique index. Fails LOUDLY with a clear message if
--    duplicates exist, so operators run the dedupe runbook before retrying.
DO $$
DECLARE dup_count INT;
BEGIN
    SELECT COUNT(*) INTO dup_count FROM (
        SELECT url FROM file_attachments GROUP BY url HAVING COUNT(*) > 1
    ) x;
    IF dup_count > 0 THEN
        RAISE EXCEPTION 'V52: % duplicate file_attachments.url rows block unique index. Run dedupe before applying. See deploy-dev/runbooks/walk-photo-variants-v2.md', dup_count;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_file_attachments_url ON file_attachments(url);

-- 4) Partial index on walk_spots rows STILL NEEDING backfill (imageKeyViewer IS NULL).
--    Sweep queries filter IS NULL, so the index must match that direction.
--    Drop the legacy IS NOT NULL index if present (it was the wrong direction for sweeps).
DROP INDEX IF EXISTS idx_walk_spots_image_key_viewer;
CREATE INDEX IF NOT EXISTS idx_walk_spots_image_key_viewer_null ON walk_spots(id)
    WHERE image_key_viewer IS NULL;
