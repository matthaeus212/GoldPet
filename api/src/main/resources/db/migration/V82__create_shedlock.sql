-- ShedLock JDBC LockProvider 백킹 테이블.
-- 멀티인스턴스(스케일아웃) 환경에서 @Scheduled 잡의 중복발화를 DB 락으로 방지한다.
-- ShedLock 공식 PostgreSQL 스키마 그대로 (net.javacrumbs.shedlock:shedlock-provider-jdbc-template).
-- plain CREATE TABLE — CONCURRENTLY 아님(인덱스 동시생성 DDL 아니므로 hang 위험 없음).
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
