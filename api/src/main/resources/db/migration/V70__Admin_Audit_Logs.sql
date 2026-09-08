-- V70: Admin audit log 보강 — Sprint 2 BLOCKER #7 (audit 부분).
-- 사전 상태: V12 마이그레이션이 admin_audit_logs 테이블 + 기본 인덱스 3건을 이미 생성.
--           AdminAuditService 가 details TEXT 컬럼에 JSON serialized payload (AdminAuditDetailsCodec) 적재 운영 중.
-- 본 마이그레이션은 신규 테이블 생성이 아니라 **보강(non-destructive)** 만 수행:
--   1. user_agent 컬럼 추가 — 운영 가시성 (어떤 브라우저/CLI 에서 호출됐는지 추적)
--   2. (action, created_at DESC) 복합 인덱스 — action 별 활동 조회 최적화 (idx_admin_audit_admin_id 단독 인덱스로는 action 필터 비효율)

ALTER TABLE admin_audit_logs ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_admin_audit_action_time ON admin_audit_logs(action, created_at DESC);

COMMENT ON COLUMN admin_audit_logs.user_agent IS 'V70 추가 — 운영자 브라우저/CLI 식별';
