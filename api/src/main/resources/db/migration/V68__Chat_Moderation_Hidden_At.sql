-- V68: Chat 모더레이션 자동 hide — Sprint 3 BLOCKER #5 (Apple App Store Review Guideline 1.2).
-- 사전 상태:
--   Report 도메인 운영 중 (ReportType=POST/COMMENT/USER/CHAT/COURSE/WALK_SPOT).
--   ReportService.checkAutoSanction 임계값 (REPORT_AUTO_HIDE_THRESHOLD, default 5) 자동 hide 구현 — POST/COMMENT/COURSE 만.
--   CHAT/USER/WALK_SPOT 은 "자동 숨김 대상 아님" else 분기로 미구현.
--   ChatMessage.deletedAt (사용자 삭제) 만 존재. admin/자동 hide 구분 없음.
--
-- 본 마이그레이션은 CHAT 자동 hide 활성화를 위한 hidden_at 컬럼 보강.
-- deletedAt (사용자 삭제) 와 의미 분리 — admin/자동 신고 처리는 hidden_at.

ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS hidden_at TIMESTAMP;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS hidden_reason VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_chat_messages_hidden ON chat_messages(hidden_at) WHERE hidden_at IS NOT NULL;

COMMENT ON COLUMN chat_messages.hidden_at      IS 'V68 — admin/자동 신고 처리 hide 시점. NULL 이면 정상 노출. deleted_at 과 의미 분리.';
COMMENT ON COLUMN chat_messages.hidden_reason  IS 'V68 — hide 사유 (자동 제재 신고 누적 수 등).';
