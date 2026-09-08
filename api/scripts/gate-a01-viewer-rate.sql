-- gate-a01-viewer-rate.sql
-- A0-1 Gate: 신규 업로드 viewer 생성률 ≥95%
-- 사용법: psql -h <HOST> -U <USER> -d <DBNAME> -v ON_ERROR_STOP=1 -f gate-a01-viewer-rate.sql
--         환경변수 PGPASSWORD 로 비밀번호 주입 권장
--
-- 출력: viewer_rate (0.0~1.0), pass/fail 판정
-- exit code: psql 자체 에러 시 non-zero (ON_ERROR_STOP=1)
-- pass 판정은 viewer_rate ≥ 0.95 일 때 'PASS', 미만이면 'FAIL'

\timing off
\pset format unaligned
\pset tuples_only off
\pset footer off

\echo '=== A0-1: Viewer 생성률 Gate (최근 24h 신규 업로드) ==='

WITH stats AS (
    SELECT
        count(*)                                              AS total,
        count(*) FILTER (WHERE viewer_url IS NOT NULL)       AS with_viewer,
        count(*) FILTER (WHERE viewer_url IS NOT NULL)::float
            / NULLIF(count(*), 0)                            AS viewer_rate
    FROM file_attachments
    WHERE file_type   = 'IMAGE'
      AND mime_type  <> 'image/gif'
      AND created_at  > NOW() - INTERVAL '24 hours'
)
SELECT
    total,
    with_viewer,
    round(viewer_rate::numeric, 4)  AS viewer_rate,
    CASE
        WHEN viewer_rate >= 0.95 THEN 'PASS ✓'
        ELSE                          'FAIL ✗  (target ≥ 0.95)'
    END                             AS gate_result
FROM stats;

-- 실패 시 대응: T0-1 재검증, walk_photo_variant_rejected_total Counter 확인
-- SELECT name, value FROM pg_stat_activity WHERE ... (큐 포화 여부)
