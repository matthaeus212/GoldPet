-- T0-6: add updated_at to file_attachments for variant-retry cooldown.
-- The FILE_ATTACHMENTS_RETRY backfill scope uses `updated_at < cutoff` so rows that
-- hit permanent S3/decode failures don't get re-processed every sweep. Per-row
-- updateVariantUrls/updateAllVariantUrls statements bump this column explicitly.

ALTER TABLE file_attachments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Backfill existing rows to created_at so the first retry sweep's cutoff is meaningful.
UPDATE file_attachments SET updated_at = created_at WHERE updated_at = CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_file_attachments_updated_at ON file_attachments(updated_at);
