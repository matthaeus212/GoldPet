#!/usr/bin/env bash
# gate-a04-cache-control.sh — A0-4 Gate: S3 응답 Cache-Control 헤더 검증
#
# 사용법:
#   ./gate-a04-cache-control.sh <S3_PUBLIC_URL>
#
# 파라미터:
#   S3_PUBLIC_URL   신규 업로드된 파일의 공개 URL 예시
#                   https://s3.mannamsquare.com/goldpet-public/<uuid>.jpg
#                   (배포 후 실제 업로드한 파일 URL 을 제공해야 합니다)
#
# 환경변수 (선택):
#   DB_URL           postgresql://... — 제공 시 DB에서 최신 viewer_url 자동 조회
#   REQUIRED_CC      검증할 Cache-Control 값 (기본: "public, max-age=31536000, immutable")
#
# 출력: Slack 붙여넣기 가능 포맷
# exit code: 0=pass, 1=fail/error

set -euo pipefail

TARGET_URL="${1:-}"
REQUIRED_CC="${REQUIRED_CC:-public, max-age=31536000, immutable}"
DB_URL="${DB_URL:-}"

HR="────────────────────────────────────────────────────────────"

usage() {
  echo "Usage: $0 <S3_PUBLIC_URL>"
  echo "  ex)  $0 'https://s3.mannamsquare.com/goldpet-public/some-uuid.jpg'"
  echo ""
  echo "  DB_URL 환경변수 설정 시 DB에서 최신 viewer_url 자동 조회:"
  echo "  DB_URL='postgresql://user:pass@host/db' $0"
  exit 1
}

# URL 이 없고 DB_URL 이 있으면 DB 에서 최신 URL 가져오기
if [[ -z "$TARGET_URL" && -n "$DB_URL" ]]; then
  echo "[INFO] DB_URL 에서 최신 viewer_url 조회 중..."
  TARGET_URL=$(psql "$DB_URL" -t -A -c \
    "SELECT viewer_url FROM file_attachments
     WHERE file_type='IMAGE' AND mime_type<>'image/gif'
       AND viewer_url IS NOT NULL
       AND created_at > NOW() - INTERVAL '1 hour'
     ORDER BY created_at DESC LIMIT 1;")
  if [[ -z "$TARGET_URL" ]]; then
    echo "[ERROR] 최근 1시간 내 viewer_url 이 없습니다. 배포 후 업로드를 먼저 수행하세요."
    exit 1
  fi
  echo "[INFO] 조회된 URL: $TARGET_URL"
fi

if [[ -z "$TARGET_URL" ]]; then
  usage
fi

echo "$HR"
echo "=== A0-4: S3 Cache-Control 헤더 Gate ==="
echo "URL     : $TARGET_URL"
echo "Expect  : Cache-Control: $REQUIRED_CC"
echo "$HR"

# HEAD 요청으로 헤더 수집
HEADERS=$(curl -sI --max-time 15 "$TARGET_URL")
if [[ $? -ne 0 ]]; then
  echo "[ERROR] URL 요청 실패. 네트워크/URL 확인 필요."
  exit 1
fi

# Cache-Control 헤더 추출 (대소문자 무관)
CC_VALUE=$(echo "$HEADERS" | grep -i '^cache-control:' | sed 's/^[Cc]ache-[Cc]ontrol:\s*//' | tr -d '\r')

echo "HTTP Headers (cache 관련):"
echo "$HEADERS" | grep -iE '^(cache-control|expires|pragma|x-amz|etag|content-type|content-length):' || echo "  (없음)"
echo "$HR"
echo "Cache-Control 값: ${CC_VALUE:-<없음>}"
echo "$HR"

# 검증: required 값이 포함되어 있는지 확인
if echo "$CC_VALUE" | grep -qi "immutable" && \
   echo "$CC_VALUE" | grep -qi "public" && \
   echo "$CC_VALUE" | grep -qi "max-age=31536000"; then
  echo "Result  : PASS ✓"
  exit 0
else
  echo "Result  : FAIL ✗"
  echo ""
  echo "[대응 방법]"
  echo "  1. S3 응답 헤더에 Cache-Control 없음 → T0-2 S3 PUT 메타데이터 적용 확인"
  echo "  2. nginx 가 S3 헤더를 덮어씀 → deploy-dev/nginx/.../mannam.conf"
  echo "     proxy_hide_header Cache-Control; 추가 여부 검토"
  echo "  3. S3 upstream 이 'immutable' 이미 보내는 경우 nginx add_header 중복 → 그대로 유지 OK"
  exit 1
fi
