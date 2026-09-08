-- Add original_file_name to file_attachments
ALTER TABLE file_attachments ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(500);
