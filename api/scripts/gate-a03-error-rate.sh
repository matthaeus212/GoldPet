#!/usr/bin/env bash
# gate-a03-error-rate.sh — A0-3 Gate: variant 에러율 변동 <2%
#
# 사용법:
#   ./gate-a03-error-rate.sh <PROMETHEUS_URL>
#
# 파라미터:
#   PROMETHEUS_URL  예) http://localhost:9090  또는  https://api.mannamsquare.com
#                   /actuator/prometheus 엔드포인트를 직접 scrape 하는 경우:
#                   PROMETHEUS_URL=http://api-host:8081  (Prometheus 서버 없이도 동작)
#
# 환경변수 (선택):
#   WINDOW_SECONDS   측정 윈도우(초) — 기본 86400 (24h)
#   BASELINE_RATIO   배포 전 에러율 (0.0~1.0) — 없으면 현재 값을 baseline 으로 저장
#   BASELINE_FILE    baseline 저장 파일 경로 — 기본 ./a03-baseline.txt
#
# 출력: Slack 붙여넣기 가능 포맷
# exit code: 0=pass, 1=fail/error

set -euo pipefail

PROMETHEUS_URL="${1:-}"
if [[ -z "$PROMETHEUS_URL" ]]; then
  echo "Usage: $0 <PROMETHEUS_URL>"
  echo "  ex)  $0 http://localhost:9090"
  echo "  ex)  $0 http://api.mannamsquare.com:8081   # actuator/prometheus 직접"
  exit 1
fi

WINDOW_SECONDS="${WINDOW_SECONDS:-86400}"
BASELINE_FILE="${BASELINE_FILE:-$(dirname "$0")/a03-baseline.txt}"

HR="────────────────────────────────────────────────────────────"

# ── Prometheus vs actuator 자동 판별 ─────────────────────────────────────────
# Prometheus PromQL 쿼리가 가능하면 사용, 아니면 actuator/prometheus raw 파싱

query_prometheus_rate() {
  local metric="$1" filter="$2"
  local url="${PROMETHEUS_URL}/api/v1/query"
  local q="sum(increase(${metric}{${filter}}[${WINDOW_SECONDS}s]))"
  curl -sf --max-time 15 "${url}?query=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$q")" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['result'][0]['value'][1] if d['data']['result'] else '0')"
}

query_actuator_metric() {
  local name="$1" result_label="$2"
  curl -sf --max-time 15 "${PROMETHEUS_URL}/actuator/prometheus" \
    | grep "^${name}{" \
    | grep "result=\"${result_label}\"" \
    | awk '{sum+=$2} END {print (sum+0)}' \
    || echo "0"
}

echo "$HR"
echo "=== A0-3: variant 에러율 Gate (window: ${WINDOW_SECONDS}s) ==="
echo "Prometheus/API : $PROMETHEUS_URL"
echo "$HR"

# actuator 직접 scrape 방식 사용 (Prometheus 서버 불필요)
SUCCESS=$(query_actuator_metric "file_variant_generate_total" "success")
ERROR=$(query_actuator_metric "file_variant_generate_total" "error")
TOTAL=$(python3 -c "print($SUCCESS + $ERROR)")

if [[ "$TOTAL" == "0" ]]; then
  echo "[WARN] file_variant_generate_total 카운터가 0 입니다."
  echo "       A0-6 gate 확인 후 재시도하세요 (Counter 미등록 가능성)."
  exit 1
fi

CURRENT_RATIO=$(python3 -c "print(round($ERROR / $TOTAL, 4))")
CURRENT_PCT=$(python3 -c "print(round($ERROR / $TOTAL * 100, 2))")

echo "Success : $SUCCESS"
echo "Error   : $ERROR"
echo "Total   : $TOTAL"
echo "Error % : ${CURRENT_PCT}%"

# baseline 처리
if [[ -f "$BASELINE_FILE" ]]; then
  BASELINE_RATIO=$(cat "$BASELINE_FILE")
  DELTA=$(python3 -c "print(round(abs($CURRENT_RATIO - $BASELINE_RATIO), 4))")
  DELTA_PCT=$(python3 -c "print(round(abs($CURRENT_RATIO - $BASELINE_RATIO) * 100, 2))")
  echo "Baseline: $(python3 -c "print(round(float('$BASELINE_RATIO')*100,2))")%  (저장: $BASELINE_FILE)"
  echo "Delta   : ${DELTA_PCT}%  (target <2%)"
  echo "$HR"

  PASS=$(python3 -c "print('yes' if abs($CURRENT_RATIO - $BASELINE_RATIO) < 0.02 else 'no')")
  if [[ "$PASS" == "yes" ]]; then
    echo "Result  : PASS ✓"
    exit 0
  else
    echo "Result  : FAIL ✗  (delta ${DELTA_PCT}% ≥ 2% — 에러 로그 grep, 큐/메모리/스레드 원인 분석, 2% 초과 시 즉시 롤백)"
    exit 1
  fi
else
  echo "[INFO] Baseline 파일 없음 → 현재 값을 baseline 으로 저장합니다."
  echo "$CURRENT_RATIO" > "$BASELINE_FILE"
  echo "Baseline 저장: $BASELINE_FILE  (${CURRENT_PCT}%)"
  echo "배포 후 다시 실행하면 delta 비교를 수행합니다."
  echo "$HR"
  echo "Result  : BASELINE SAVED (재실행 대기)"
  exit 0
fi
