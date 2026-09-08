#!/usr/bin/env bash
# gate-b01-community-author-backfill.sh — B0-1 Gate: community 게시글 이미지 backfill 상태
#
# community-author-profile-gallery Phase 1 배포 전 실행.
# thumbnail_url / viewer_url variant 미생성 비율이 각각 1% 이하여야 통과(exit 0).
# 하나라도 초과하면 exit 1 — 배포 차단 신호.
#
# 환경변수:
#   DB_HOST      DB 호스트     (기본: localhost)
#   DB_PORT      DB 포트       (기본: 5433)
#   DB_NAME      DB 이름       (기본: goldpet)
#   DB_USER      DB 사용자     (기본: goldpet)
#   DB_PASSWORD  DB 비밀번호   (기본: goldpet123)
#
# 사용:
#   ./gate-b01-community-author-backfill.sh                                   # 로컬
#   DB_HOST=101.250.201.36 DB_PORT=5432 DB_PASSWORD=<pw> \
#     ./gate-b01-community-author-backfill.sh                                 # dev 서버

set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5433}"
DB_NAME="${DB_NAME:-goldpet}"
DB_USER="${DB_USER:-goldpet}"
export PGPASSWORD="${DB_PASSWORD:-goldpet123}"

THRESHOLD="0.01"   # 1%
HR="══════════════════════════════════════════════════════════"

# psql 경로 탐색 (Homebrew libpq 우선)
if command -v psql &>/dev/null; then
    PSQL="psql"
elif [[ -x "/opt/homebrew/opt/libpq/bin/psql" ]]; then
    PSQL="/opt/homebrew/opt/libpq/bin/psql"
else
    echo "[ERROR] psql 을 찾을 수 없습니다. brew install libpq 또는 postgresql 설치 후 재시도하세요."
    exit 1
fi

# ── DB 연결 확인 ────────────────────────────────────────────────────────────
if ! "$PSQL" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        -c "SELECT 1" -t -A &>/dev/null; then
    echo "[ERROR] DB 연결 실패: $DB_USER@$DB_HOST:$DB_PORT/$DB_NAME"
    echo "        deploy-local 이 기동 중인지 확인하세요: ./deploy-local/scripts/start.sh"
    exit 1
fi

# ── 쿼리 실행 헬퍼 ──────────────────────────────────────────────────────────
run_sql() {
    "$PSQL" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
            -t -A -c "$1" 2>/dev/null
}

echo "$HR"
echo " B0-1 Gate: Community Author Gallery — Variant Backfill 상태 측정"
echo " DB  : $DB_USER@$DB_HOST:$DB_PORT/$DB_NAME"
echo " 임계값: missing_ratio ≤ $THRESHOLD (1%)"
echo " 시각: $(date '+%Y-%m-%d %H:%M:%S')"
echo "$HR"
echo ""

# ── 공통 집계 (단일 쿼리로 thumbnail + viewer 동시 측정) ────────────────────
# 형식: <variant>|<total>|<missing_count>|<missing_ratio>
RESULTS=$(run_sql "
SELECT
    variant,
    total::text,
    missing_count::text,
    round(missing_ratio::numeric, 6)::text AS missing_ratio
FROM (
    SELECT
        'thumbnail_url'::text                                                  AS variant,
        count(*)                                                               AS total,
        count(*) FILTER (WHERE fa.thumbnail_url IS NULL)                      AS missing_count,
        count(*) FILTER (WHERE fa.thumbnail_url IS NULL)::float
            / NULLIF(count(*), 0)                                             AS missing_ratio
    FROM file_attachments fa
    JOIN community_post_images cpi ON cpi.file_id = fa.id
    UNION ALL
    SELECT
        'viewer_url'::text,
        count(*),
        count(*) FILTER (WHERE fa.viewer_url IS NULL),
        count(*) FILTER (WHERE fa.viewer_url IS NULL)::float / NULLIF(count(*), 0)
    FROM file_attachments fa
    JOIN community_post_images cpi ON cpi.file_id = fa.id
) sub
ORDER BY variant;
")

if [[ -z "$RESULTS" ]]; then
    echo "[WARN] 쿼리 결과가 비어 있습니다."
    echo "       community_post_images 테이블에 row 가 없거나 file_attachments join 실패."
    echo "       테이블 구조 및 데이터를 확인하세요."
    exit 1
fi

# ── 결과 파싱 및 게이트 판정 ─────────────────────────────────────────────────
GATE_PASS=true

while IFS='|' read -r variant total missing_count missing_ratio; do
    [[ -z "$variant" ]] && continue

    total="${total:-0}"
    missing_count="${missing_count:-0}"
    missing_ratio="${missing_ratio:-0}"

    # awk 로 부동소수 비교 (bash 는 float 비교 불가)
    FAIL=$(awk -v r="$missing_ratio" -v t="$THRESHOLD" \
               'BEGIN { print (r != "" && r+0 > t+0) ? "1" : "0" }')

    echo "  [$variant]"
    printf  "    total         : %s\n" "$total"
    printf  "    missing_count : %s\n" "$missing_count"
    printf  "    missing_ratio : %s\n" "$missing_ratio"

    if [[ "$total" == "0" ]]; then
        printf "    gate_result   : N/A (이미지 row 없음)\n"
    elif [[ "$FAIL" == "1" ]]; then
        printf "    gate_result   : ❌ FAIL  (%.4f > %s) — backfill 먼저 실행\n" \
               "$missing_ratio" "$THRESHOLD"
        GATE_PASS=false
    else
        printf "    gate_result   : ✅ PASS  (%.4f ≤ %s)\n" \
               "$missing_ratio" "$THRESHOLD"
    fi
    echo ""
done <<< "$RESULTS"

# ── 최종 판정 ──────────────────────────────────────────────────────────────
echo "$HR"
if [[ "$GATE_PASS" == "true" ]]; then
    echo " ✅ GATE PASSED — Day 3 QA 진입 가능"
    echo ""
    echo " 다음 단계:"
    echo "   1. dev 서버에 backend 배포 (V58 인덱스 포함)"
    echo "   2. community.author_profile_link.enabled = false 로 플래그 off 상태 확인"
    echo "   3. E2E 체크리스트 12개 항목 수행 후 플래그 ON"
    echo "$HR"
    exit 0
else
    echo " ❌ GATE FAILED — variant backfill 작업을 먼저 실행하세요"
    echo ""
    echo " 조치 방법:"
    echo "   · walk-photo-v2 backfill endpoint 패턴 재활용"
    echo "   · POST /api/v1/admin/files/backfill-variants (또는 동등 endpoint)"
    echo "   · backfill 완료 후 이 스크립트를 재실행하세요"
    echo "   · 참고: .omc/memory/project_walk_photo_v2_state.md"
    echo "$HR"
    exit 1
fi
