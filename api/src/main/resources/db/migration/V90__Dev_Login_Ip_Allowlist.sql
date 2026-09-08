-- dev-login 허용 IP 목록 (관리자 CRUD 대상)
--
-- dev-login 은 비밀번호·SNS 인증 없이 토큰을 발급하므로, 접근 가능한 출발지 IP 를 명시적으로
-- 관리한다. 기존에는 SystemSetting 문자열(콤마구분)이었는데, 라벨·만료·개별 on/off·감사가 없어
-- 관리자 화면으로 다루기 어려웠다.
--
-- CONCURRENTLY 금지 — Flyway 가 스키마 이력 트랜잭션을 잡은 채 대기해 자기 자신과 교착한다(V87 전례).

CREATE TABLE IF NOT EXISTS dev_login_ip_allowlist (
    id              BIGSERIAL PRIMARY KEY,
    -- 단일 IP 또는 CIDR (예: 115.79.198.72, 10.1.2.0/24, ::1)
    ip_pattern      VARCHAR(64)  NOT NULL,
    -- 누구/어디인지 알아볼 수 있게. 라벨 없는 IP 는 나중에 지워도 되는지 아무도 모른다.
    label           VARCHAR(200) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 임시 QA IP 는 만료를 걸어 방치되지 않게 한다. NULL = 무기한.
    expires_at      TIMESTAMP,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_dev_login_ip_allowlist_pattern
    ON dev_login_ip_allowlist (ip_pattern);

-- 판정 쿼리(enabled + 미만료)용
CREATE INDEX IF NOT EXISTS idx_dev_login_ip_allowlist_enabled
    ON dev_login_ip_allowlist (enabled, expires_at);

-- 기존 SystemSetting(devlogin.allowed.ips) 값을 그대로 이관한다.
-- 이 시딩이 없으면 배포 즉시 전 IP 가 차단되어 오너가 잠긴다.
INSERT INTO dev_login_ip_allowlist (ip_pattern, label, enabled)
VALUES
    ('115.79.198.72',   '오너 접속 IP (베트남)', TRUE),
    ('211.207.145.74',  '오너 접속 IP (한국)',   TRUE),
    ('113.161.75.215',  '오너 접속 IP (베트남)', TRUE),
    ('220.117.201.51',  '오너 접속 IP (한국)',   TRUE),
    ('127.0.0.1',       '로컬 개발/e2e',         TRUE),
    ('::1',             '로컬 개발/e2e (IPv6)',  TRUE)
ON CONFLICT (ip_pattern) DO NOTHING;
