-- 커뮤니티 트렌딩(인기) 랭킹 윈도우 스캔 커버용 인덱스.
--
-- 트렌딩 쿼리(CommunityPostRepository.findTrending)는 `is_hidden = false AND created_at >= :since`
-- 후보를 시간감쇠 score 로 정렬한다. score 는 계산값이라 정렬은 인덱스로 대체 불가하지만,
-- 최근 N일 윈도우 후보 집합을 좁히는 데 `(created_at) WHERE is_hidden = false` partial index 가 seq scan 을 회피.
--
-- 주의: CONCURRENTLY 미사용(일반 CREATE INDEX). community_posts 는 write-heavy 가 아니라
-- 배포 시 짧은 ACCESS EXCLUSIVE 락만 발생(허용). CONCURRENTLY 는 테스트(Flyway 마이그레이션 단계)에서
-- 병렬 컨텍스트의 열린 트랜잭션을 무한 대기해 hang 을 유발하므로 사용하지 않는다.
CREATE INDEX IF NOT EXISTS idx_community_posts_hidden_created
    ON community_posts (created_at)
    WHERE is_hidden = false;
