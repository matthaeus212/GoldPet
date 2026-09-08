-- T2: WebP variant URL columns for file_attachments
-- Generated alongside JPEG variants (_thumb.webp / _medium.webp / _viewer.webp) on new uploads.
-- Existing rows remain NULL — backfill is a separate ticket.
ALTER TABLE file_attachments
    ADD COLUMN IF NOT EXISTS thumbnail_url_webp VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS medium_url_webp    VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS viewer_url_webp    VARCHAR(2048);
