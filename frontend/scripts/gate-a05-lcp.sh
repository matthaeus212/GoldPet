#!/usr/bin/env bash
# gate-a05-lcp.sh — A0-5 Gate: HomePage Lighthouse LCP 악화 금지
#
# 사용법:
#   ./gate-a05-lcp.sh [URL] [MODE]
#
# 파라미터:
#   URL    측정 대상 URL (기본: https://app.mannamsquare.com)
#   MODE   baseline | after | compare
#          baseline: Tier 0 배포 전 3회 측정 → baseline.json 저장
#          after:    Tier 0 배포 후 3회 측정 → after.json 저장
#          compare:  baseline vs after LCP 중앙값 비교
#
# 의존성: npx lighthouse (npx 는 Node.js 에 포함)
#         수동 실행 전제 — CI 통합 없음
#
# 출력: Slack 붙여넣기 가능 포맷 + JSON 파일
#       측정 결과: .omc/measurements/lcp-{YYYYMMDD}-{mode}.json
# exit code: 0=pass, 1=fail/error

set -euo pipefail

TARGET_URL="${1:-https://app.mannamsquare.com}"
MODE="${2:-baseline}"
RUNS=3

HR="────────────────────────────────────────────────────────────"
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MEASUREMENTS_DIR="${REPO_ROOT}/.omc/measurements"
DATE_TAG=$(date +%Y%m%d)

BASELINE_FILE="${MEASUREMENTS_DIR}/lcp-baseline.json"
AFTER_FILE="${MEASUREMENTS_DIR}/lcp-after.json"
OUTPUT_FILE="${MEASUREMENTS_DIR}/lcp-${DATE_TAG}-${MODE}.json"

mkdir -p "$MEASUREMENTS_DIR"

usage() {
  echo "Usage: $0 [URL] <baseline|after|compare>"
  echo "  ex)  $0 https://app.mannamsquare.com baseline"
  echo "  ex)  $0 https://app.mannamsquare.com after"
  echo "  ex)  $0 _ compare"
  exit 1
}

if [[ ! "$MODE" =~ ^(baseline|after|compare)$ ]]; then usage; fi

run_lighthouse() {
  local url="$1" run_num="$2" out_file="$3"
  echo "[INFO] Run ${run_num}/${RUNS}: npx lighthouse $url ..."
  npx lighthouse "$url" \
    --output=json \
    --output-path="$out_file" \
    --form-factor=mobile \
    --emulated-form-factor=mobile \
    --throttling-method=simulate \
    --only-categories=performance \
    --chrome-flags="--headless --no-sandbox --disable-dev-shm-usage" \
    --quiet 2>/dev/null || {
      echo "[ERROR] Lighthouse run ${run_num} 실패"
      return 1
    }
}

extract_lcp() {
  local json_file="$1"
  python3 -c "
import json, sys
with open('$json_file') as f:
    d = json.load(f)
# LCP in milliseconds
lcp = d['audits']['largest-contentful-paint']['numericValue']
print(round(lcp, 0))
"
}

median_of() {
  # args: space-separated numbers
  python3 -c "
import sys, statistics
vals = [float(x) for x in sys.argv[1:]]
print(statistics.median(vals))
" "$@"
}

if [[ "$MODE" == "compare" ]]; then
  if [[ ! -f "$BASELINE_FILE" || ! -f "$AFTER_FILE" ]]; then
    echo "[ERROR] baseline.json 과 after.json 이 모두 필요합니다."
    echo "  Baseline: $BASELINE_FILE"
    echo "  After   : $AFTER_FILE"
    exit 1
  fi
  BASE_MEDIAN=$(python3 -c "import json; d=json.load(open('$BASELINE_FILE')); print(d['median_ms'])")
  AFTER_MEDIAN=$(python3 -c "import json; d=json.load(open('$AFTER_FILE')); print(d['median_ms'])")

  echo "$HR"
  echo "=== A0-5: Lighthouse LCP Gate ==="
  echo "URL      : $TARGET_URL"
  echo "Baseline : ${BASE_MEDIAN} ms"
  echo "After    : ${AFTER_MEDIAN} ms"

  PASS=$(python3 -c "print('yes' if float('$AFTER_MEDIAN') <= float('$BASE_MEDIAN') * 1.05 else 'no')")
  DIFF_PCT=$(python3 -c "print(round((float('$AFTER_MEDIAN') - float('$BASE_MEDIAN')) / float('$BASE_MEDIAN') * 100, 1))")

  echo "Delta    : ${DIFF_PCT}%  (허용: ≤+5% — 배포 후 ≥ 배포 전이면 T0-3 롤백)"
  echo "$HR"

  if [[ "$PASS" == "yes" ]]; then
    echo "Result   : PASS ✓  (LCP 악화 없음)"
    exit 0
  else
    echo "Result   : FAIL ✗  (LCP 악화 ${DIFF_PCT}%)"
    echo ""
    echo "[대응 방법]"
    echo "  1. T0-3 롤백: Swiper 첫 slide eager 설정 확인"
    echo "  2. DOM 검증: fetchpriority=high + loading=eager 적용 여부 확인"
    echo "  3. 배포 전 성능 회귀 가능성 → T0-3 PR rollback"
    exit 1
  fi
fi

# baseline 또는 after 측정 모드
echo "$HR"
echo "=== A0-5: Lighthouse LCP 측정 (${MODE}, ${RUNS}회) ==="
echo "URL  : $TARGET_URL"
echo "Mode : $MODE"
echo "$HR"

LCP_VALUES=()
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

for i in $(seq 1 $RUNS); do
  OUT="${TEMP_DIR}/lh-run${i}.json"
  if run_lighthouse "$TARGET_URL" "$i" "$OUT"; then
    LCP=$(extract_lcp "$OUT")
    LCP_VALUES+=("$LCP")
    echo "  Run ${i}: LCP = ${LCP} ms"
  else
    echo "  Run ${i}: 실패 (건너뜀)"
  fi
done

if [[ ${#LCP_VALUES[@]} -lt 2 ]]; then
  echo "[ERROR] 유효한 측정값이 ${#LCP_VALUES[@]}개로 부족합니다 (최소 2회 필요)."
  exit 1
fi

MEDIAN=$(median_of "${LCP_VALUES[@]}")
echo "$HR"
echo "측정값  : ${LCP_VALUES[*]} ms"
echo "중앙값  : ${MEDIAN} ms"

# 결과 저장
python3 -c "
import json, sys
from datetime import datetime
data = {
  'mode': '$MODE',
  'url': '$TARGET_URL',
  'runs': ${#LCP_VALUES[@]},
  'values_ms': [float(x) for x in '${LCP_VALUES[*]}'.split()],
  'median_ms': float('$MEDIAN'),
  'measured_at': datetime.utcnow().isoformat() + 'Z'
}
with open('$OUTPUT_FILE', 'w') as f:
  json.dump(data, f, indent=2)
print(f'[INFO] 결과 저장: $OUTPUT_FILE')
"

# 심볼릭 링크 갱신 (baseline / after → 최신 파일)
LINK_TARGET="$( [[ "$MODE" == "baseline" ]] && echo "$BASELINE_FILE" || echo "$AFTER_FILE" )"
cp "$OUTPUT_FILE" "$LINK_TARGET"
echo "[INFO] 최신 파일 업데이트: $LINK_TARGET"
echo "$HR"
echo "[NEXT] compare 모드로 비교: $0 _ compare"
