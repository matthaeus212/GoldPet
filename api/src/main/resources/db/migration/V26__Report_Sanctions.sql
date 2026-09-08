-- 1. 기존 resolved_by 데이터 정리 (FK 변경 전 users 참조 데이터 무효화)
UPDATE reports SET resolved_by = NULL WHERE resolved_by IS NOT NULL;

-- 2. resolved_by FK를 admin_users로 변경
ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_resolved_by_fkey;
ALTER TABLE reports ADD CONSTRAINT reports_resolved_by_fkey
    FOREIGN KEY (resolved_by) REFERENCES admin_users(id);

-- 3. action_type 컬럼 추가 (어떤 제재를 수행했는지)
ALTER TABLE reports ADD COLUMN action_type VARCHAR(50);

-- 4. admin_note 컬럼 추가 (관리자 메모)
ALTER TABLE reports ADD COLUMN admin_note VARCHAR(500);

-- 5. reason 컬럼 길이 확장 (255 -> 500)
ALTER TABLE reports ALTER COLUMN reason TYPE VARCHAR(500);

-- 6. 중복 신고 방지 유니크 인덱스
CREATE UNIQUE INDEX idx_reports_unique_pending
    ON reports (reporter_id, type, target_id)
    WHERE status = 'PENDING';
