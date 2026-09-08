-- W2/PERF-002 — community_comments.post_id 핫패스 커버 인덱스.
--
-- ## 배경
-- `CommunityCommentRepository` 의 countByPostId / existsByPostIdAndUserId / findByPostId... 는 모두
-- `post_id` 필터를 수행하나 PostgreSQL 은 FK 컬럼을 자동 인덱싱하지 않는다(V1 스키마에 post_id 인덱스 없음).
-- 커뮤니티 피드 로드는 게시글 수만큼 이 쿼리를 반복(PERF-004)하므로 seq scan 비용이 게시글 수만큼 배가된다.
-- 대부분의 조회가 숨김 제외(`is_hidden=false`)를 함께 필터하므로 `(post_id, is_hidden)` 복합 인덱스로
-- 댓글수 집계·존재여부 쿼리를 인덱스 온리에 가깝게 커버한다.
--
-- ## CONCURRENTLY 미사용 사유 (2026-07-11 배포 데드락 후속)
-- 이전 버전은 `CREATE INDEX CONCURRENTLY`(+ `executeInTransaction=false`)였으나, Flyway 앱 기동 시
-- 스키마-히스토리 트랜잭션 커넥션(idle in transaction)과 CONCURRENTLY 빌드가 서로를 기다리는 **자기
-- 데드락**으로 API 기동이 멈추는 사고가 발생했다(dev DB 배포 중 재현). `community_comments` 는 소형
-- 테이블이라 CONCURRENTLY 의 무잠금 이점이 실익이 없고, 일반 `CREATE INDEX` 의 짧은 SHARE 락(빌드 순간)
-- 이 훨씬 안전하다. 따라서 트랜잭션 내 일반 `CREATE INDEX` 로 전환한다(단문 → Flyway 트랜잭션에서
-- 원자적으로 성공/롤백하므로 INVALID 인덱스 잔존 불가). 대형 테이블 대상이었다면 CONCURRENTLY 를
-- 유지하되 배포 스크립트에서 마이그레이션 외부(psql)로 선적용하는 방식을 택했을 것이다.

CREATE INDEX IF NOT EXISTS idx_community_comments_post_id_hidden
    ON community_comments (post_id, is_hidden);
