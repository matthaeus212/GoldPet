-- gate-b01-community-author-backfill.sql
-- B0-1 Gate: community 게시글 이미지 thumbnail_url / viewer_url variant 미생성 비율 ≤1%
--
-- 배포 전 실행 필수 (community-author-profile-gallery Phase 1).
-- missing_ratio > 0.01 이면 'FAIL ✗' — variant backfill endpoint 선 실행 후 재측정.
-- 두 variant 모두 'PASS ✓' 이어야 Day 3 QA 진입 가능.
--
-- 사용법:
--   psql -h localhost -p 5433 -U goldpet -d goldpet \
--        -v ON_ERROR_STOP=1 -f gate-b01-community-author-backfill.sql
--
--   # dev 서버:
--   PGPASSWORD=<pw> psql -h 101.250.201.36 -p 5432 -U goldpet -d goldpet \
--        -v ON_ERROR_STOP=1 -f gate-b01-community-author-backfill.sql

\timing off
\pset format aligned
\pset tuples_only off
\pset footer off

\echo ''
\echo '=== B0-1: Community Author Gallery — Variant Backfill Gate ==='
\echo '    대상: community_post_images 에 연결된 file_attachments'
\echo '    임계값: missing_ratio ≤ 0.01 (1%)'
\echo ''

-- thumbnail_url + viewer_url 미생성 비율을 한 결과셋으로 출력
SELECT
    variant,
    total,
    missing_count,
    round(missing_ratio::numeric, 6)                AS missing_ratio,
    CASE
        WHEN total = 0         THEN 'N/A (이미지 없음)'
        WHEN missing_ratio <= 0.01 THEN 'PASS ✓'
        ELSE                        'FAIL ✗  (backfill 필요, target ≤ 0.01)'
    END                                             AS gate_result
FROM (
    -- thumbnail_url
    SELECT
        'thumbnail_url'::text                                                   AS variant,
        count(*)                                                                AS total,
        count(*) FILTER (WHERE fa.thumbnail_url IS NULL)                       AS missing_count,
        count(*) FILTER (WHERE fa.thumbnail_url IS NULL)::float
            / NULLIF(count(*), 0)                                              AS missing_ratio
    FROM file_attachments fa
    JOIN community_post_images cpi ON cpi.file_id = fa.id

    UNION ALL

    -- viewer_url
    SELECT
        'viewer_url'::text                                                      AS variant,
        count(*)                                                                AS total,
        count(*) FILTER (WHERE fa.viewer_url IS NULL)                         AS missing_count,
        count(*) FILTER (WHERE fa.viewer_url IS NULL)::float
            / NULLIF(count(*), 0)                                              AS missing_ratio
    FROM file_attachments fa
    JOIN community_post_images cpi ON cpi.file_id = fa.id
) sub
ORDER BY variant;

\echo ''
\echo '판정 기준:'
\echo '  PASS ✓  → 두 항목 모두 PASS 면 Day 3 QA 진입 가능'
\echo '  FAIL ✗  → variant backfill endpoint 실행 후 재측정'
\echo '             (walk-photo-v2 backfill endpoint 패턴 재활용)'
\echo '  N/A     → community_post_images 테이블에 이미지 row 없음'
\echo ''
