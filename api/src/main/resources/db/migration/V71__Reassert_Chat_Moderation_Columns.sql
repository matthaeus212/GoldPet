-- V71: chat_messages 모더레이션 컬럼 idempotent 재적용 (corrective forward migration).
--
-- 배경: V68(Chat_Moderation_Hidden_At)이 V69/V70 보다 낮은 버전이지만 나중에 추가되어,
-- Flyway 기본값(out-of-order=false) 환경에서 V69/V70 이 먼저 적용된 DB 에서는 V68 이 조용히 스킵됐다.
-- 그 결과 chat_messages.hidden_at / hidden_reason 컬럼이 누락 → ChatMessageRepository.findVisible*
-- 쿼리(`AND m.hidden_at IS NULL AND m.deleted_at IS NULL`)가 SQLGrammarException 으로 실패하고,
-- BLOCKER #5(채팅 모더레이션 자동 hide) 가 운영에서 조용히 깨진다.
--
-- 본 마이그레이션은 V38(deleted_at) + V68(hidden_at/hidden_reason/index) 의 변경을 모두
-- IF NOT EXISTS 로 재적용해, 어떤 DB 상태(V68 스킵 여부 무관)에서도 컬럼 존재를 보장한다.
-- (application-dev/prod.yml 의 spring.flyway.out-of-order=true 와 함께 belt & suspenders 로 동작.)

-- V38 재확인 — 사용자 삭제 시점.
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- V68 재확인 — admin/자동 신고 처리 hide.
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS hidden_at TIMESTAMP;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS hidden_reason VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_chat_messages_hidden ON chat_messages(hidden_at) WHERE hidden_at IS NOT NULL;

COMMENT ON COLUMN chat_messages.hidden_at      IS 'V68/V71 — admin/자동 신고 처리 hide 시점. NULL 이면 정상 노출. deleted_at 과 의미 분리.';
COMMENT ON COLUMN chat_messages.hidden_reason  IS 'V68/V71 — hide 사유 (자동 제재 신고 누적 수 등).';
