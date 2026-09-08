-- 단일 이미지 → 다중 이미지 지원 (JSON 배열)
ALTER TABLE app_notices ADD COLUMN image_urls TEXT;

UPDATE app_notices
SET image_urls = '["' || image_url || '"]'
WHERE image_url IS NOT NULL AND image_url != '';

ALTER TABLE app_notices DROP COLUMN image_url;
