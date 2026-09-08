#!/usr/bin/env bash
# gate-a06-counters.sh — A0-6 Gate: Counter 3종 non-zero 확인
#
# 사용법:
#   ./gate-a06-counters.sh <API_BASE_URL>
#
# 파라미터:
#   API_BASE_URL   Spring Boot actuator 가 노출된 URL
#                  예) http://localhost:8081
#                      https://api.mannamsquare.com
#
# 환경변수 (선택):
#   ACTUATOR_PATH  actuator endpoint 경로 (기본: /actuator/prometheus)
#
# 검증 대상 Counter:
#   1. file_variant_generate_total   (result=success 또는 error 중 하나라도 non-zero)
#   2. file_variant_generate_latency (timer count non-zero)
#   3. file_presigned_expired_fallback_total (non-zero — presigned 만료 fallback 발생 시)
#      ※ fallback counter 는 사용자 요청 없으면 0 일 수 있음 — 경고만 출력
#
# 출력: Slack 붙여넣기 가능 포맷
# exit code: 0=pass, 1=fail

set -euo pipefail

API_BASE_URL="${1:-}"
ACTUATOR_PATH="${ACTUATOR_PATH:-/actuator/prometheus}"

HR="────────────────────────────────────────────────────────────"

usage() {
  echo "Usage: $0 <API_BASE_URL>"
  echo "  ex)  $0 http://localhost:8081"
  echo "  ex)  $0 https://api.mannamsquare.com"
  exit 1
}

if [[ -z "$API_BASE_URL" ]]; then usage; fi

ENDPOINT="${API_BASE_URL%/}${ACTUATOR_PATH}"

echo "$HR"
echo "=== A0-6: Counter 3종 non-zero Gate ==="
echo "Endpoint: $ENDPOINT"
echo "$HR"

# Prometheus 텍스트 scrape
PROM_DATA=$(curl -sf --max-time 20 "$ENDPOINT") || {
  echo "[ERROR] actuator/prometheus 엔드포인트 응답 없음"
  echo "        API 서버 기동 여부 / ACTUATOR_PATH 확인"
  exit 1
}

check_counter() {
  local label="$1" pattern="$2" warn_only="${3:-no}"
  local lines
  lines=$(echo "$PROM_DATA" | grep -E "^${pattern}" 2>/dev/null || true)

  if [[ -z "$lines" ]]; then
    if [[ "$warn_only" == "yes" ]]; then
      echo "  [WARN] $label — 메트릭 미등록 (경고만, gate 미차단)"
      return 0
    fi
    echo "  [FAIL] $label — 메트릭 자체가 없음 (Counter 이름 오타 또는 Micrometer 미등록)"
    return 1
  fi

  local total
  total=$(echo "$lines" | awk '{sum+=$2} END {print sum+0}')
  local nonzero
  nonzero=$(python3 -c "print('yes' if float('$total') > 0 else 'no')")

  if [[ "$nonzero" == "yes" ]]; then
    echo "  [PASS] $label — total=$total ✓"
    return 0
  else
    if [[ "$warn_only" == "yes" ]]; then
      echo "  [WARN] $label — 값=0 (presigned 만료 fallback 미발생 — 정상일 수 있음)"
      return 0
    fi
    echo "  [FAIL] $label — 값=0 (Counter 등록은 됐으나 non-zero 아님)"
    return 1
  fi
}

PASS=true

# 1. file_variant_generate_total
if ! check_counter \
  "file_variant_generate_total" \
  "file_variant_generate_total\\{"; then
  PASS=false
fi

# 2. file_variant_generate_latency (Timer → _count suffix)
if ! check_counter \
  "file_variant_generate_latency (timer count)" \
  "file_variant_generate_latency_(count|seconds_count)\\{"; then
  PASS=false
fi

# 3. file_presigned_expired_fallback_total (warn-only: presigned 만료가 아직 안 일어났을 수 있음)
check_counter \
  "file_presigned_expired_fallback_total" \
  "file_presigned_expired_fallback_total" \
  "yes"

echo "$HR"

# 관련 메트릭 raw 출력 (디버깅용)
echo "[DEBUG] 관련 메트릭 raw (상위 20줄):"
echo "$PROM_DATA" \
  | grep -E "^file_variant_|^file_presigned_" \
  | grep -v "^#" \
  | head -20 \
  || echo "  (없음)"

echo "$HR"

if [[ "$PASS" == "true" ]]; then
  echo "Result  : PASS ✓  (핵심 Counter 2종 non-zero)"
  exit 0
else
  echo "Result  : FAIL ✗"
  echo ""
  echo "[대응 방법]"
  echo "  1. Counter 이름 오타 → FileService.kt meterRegistry.counter() 호출 확인"
  echo "  2. Micrometer tag cardinality 폭주 → result/category 태그 값 종류 확인"
  echo "  3. 업로드 후 재측정 (Counter 는 최소 1회 storeFile 호출 필요)"
  exit 1
fi
