-- WI-1: Banner placement(노출 위치) 추가.
-- 하드게이트: dev DB `SELECT count(*) FROM banners` = 1행(소수) 확인됨 → 단순 HOME 일괄 backfill.
-- (행 다수였다면 is_active=false 동반했겠으나, 1행이라 의도치 않은 HOME 다수노출 위험 없음.)
-- plain DDL only (CONCURRENTLY 금지). ADD COLUMN → backfill → NOT NULL 순.

ALTER TABLE banners ADD COLUMN placement VARCHAR(20);

UPDATE banners SET placement = 'HOME' WHERE placement IS NULL;

ALTER TABLE banners ALTER COLUMN placement SET NOT NULL;

CREATE INDEX idx_banners_placement_active ON banners (placement, is_active, display_order);
