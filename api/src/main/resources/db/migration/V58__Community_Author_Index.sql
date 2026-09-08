-- flyway:executeInTransaction=false
--
-- community-author-profile-gallery Phase 1 Day 1 — 작성자별 공개 게시글 cursor 쿼리 커버 인덱스 + 피처 플래그 seed.
--
-- ## 배경
-- `GET /api/v1/users/{userId}/community/posts` 는 `(user_id, visibility='PUBLIC', is_hidden=false)` 필터 + `(created_at DESC, id DESC)` cursor tuple 순회를 수행한다.
-- 현재 `community_posts` 테이블에는 `user_id` 복합 인덱스가 없어(V1 정의 기준) 많은 게시글을 가진 유저의
-- 프로필 갤러리 로딩이 seq scan 으로 fallback 할 위험이 있다. 이를 커버하는 복합 인덱스를 추가한다.
--
-- ## 운영 주의
-- `CREATE INDEX CONCURRENTLY` 는 트랜잭션 내부에서 실행 불가 → 상단 `flyway:executeInTransaction=false`
-- 지시자로 이 마이그레이션을 비트랜잭션 모드로 실행. `IF NOT EXISTS` 로 재시도/롤백 시 중복 생성 방지.
-- `INSERT ... ON CONFLICT DO NOTHING` 역시 idempotent.
--
-- ### Runbook: CONCURRENTLY 무한 대기 시
-- `CREATE INDEX CONCURRENTLY` 는 Postgres 서버 쪽 advisory lock 을 대기하므로, 이전 세션의
-- idle-in-transaction 연결이 남아 있으면 무한 대기한다. dev/local 에서 apply 실패 시 아래 실행 후 재시도:
--   SELECT pg_terminate_backend(pid) FROM pg_stat_activity
--    WHERE application_name LIKE 'PostgreSQL%' AND datname = 'goldpet';
-- (`Jenkinsfile` / `deploy-api.sh` 2026-04-21 업데이트분에 자동 포함됨.)

-- NOTE: `INSERT INTO system_settings` (트랜잭션 명령) 는 Flyway 가 mixed 감지를 거부하므로 V59 로 분리함.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_community_posts_author_visibility_created
    ON community_posts (user_id, visibility, created_at DESC, id DESC);
