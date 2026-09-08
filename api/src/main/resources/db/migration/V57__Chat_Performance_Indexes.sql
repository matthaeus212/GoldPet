-- flyway:executeInTransaction=false
--
-- T-chat-latency-v2 Step 3 — 채팅 hot-path 쿼리 커버용 인덱스 2종 (CONCURRENTLY).
--
-- ## 배경
-- 1) `findActiveRoomsByUserId` / 읽지 않은 대화방 카운트 계산이 `chat_room_participants` 전체 스캔을 유발
--    → `user_id` + `left_at IS NULL` partial index 로 활성 참여자만 타겟.
-- 2) 채팅 히스토리 로딩 (`ORDER BY created_at DESC LIMIT N`) 은 기존 오름차순 인덱스를 backward scan 해야 하며
--    Postgres planner 가 종종 seq scan 을 택함 → DESC 복합 인덱스 추가로 latest-first 패턴 직접 커버.
--
-- ## 운영 주의
-- `CREATE INDEX CONCURRENTLY` 는 트랜잭션 내부에서 실행 불가 → 상단 `flyway:executeInTransaction=false`
-- 지시자로 이 마이그레이션을 비트랜잭션 모드로 실행. `IF NOT EXISTS` 로 재시도/롤백 시 중복 생성 방지.
-- sender_id 단독 인덱스는 워크로드상 쿼리 없음 → 추가하지 않음 (write amplification 회피).
--
-- ### Runbook: CONCURRENTLY 무한 대기 시
-- `CREATE INDEX CONCURRENTLY` 는 Postgres 서버 쪽 advisory lock 을 대기하므로, 이전 세션의
-- idle-in-transaction 연결이 남아 있으면 무한 대기한다.
-- dev/local 에서 apply 실패 시 아래 실행 후 재시도:
--   SELECT pg_terminate_backend(pid) FROM pg_stat_activity
--    WHERE application_name LIKE 'PostgreSQL%' AND datname = 'goldpet';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crp_user_active
    ON chat_room_participants (user_id)
    WHERE left_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_messages_room_created_desc
    ON chat_messages (room_id, created_at DESC);
