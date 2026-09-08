#!/bin/bash
# apply-cors.sh — Apply CORS policy to goldpet-private bucket
#
# Usage:
#   ENV=local  ./deploy-dev/s3/apply-cors.sh   # local MinIO (docker)
#   ENV=dev    ./deploy-dev/s3/apply-cors.sh   # dev MinIO (mannamsquare.com)
#   ENV=prod   ./deploy-dev/s3/apply-cors.sh   # prod AWS S3 (goldpet.com)
#
# Prerequisites:
#   local/dev : mc (MinIO Client)  — brew install minio/stable/mc
#   prod      : aws CLI v2         — https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CORS_FILE="${SCRIPT_DIR}/goldpet-private-cors-policy.json"
PRIVATE_BUCKET="goldpet-private"
ENV="${ENV:-local}"

if [[ ! -f "$CORS_FILE" ]]; then
  echo "ERROR: CORS policy file not found: $CORS_FILE"
  exit 1
fi

echo "==> Applying CORS to '${PRIVATE_BUCKET}' (ENV=${ENV})"

case "$ENV" in

  local)
    MINIO_ALIAS="goldpet-local"
    MINIO_URL="http://localhost:9100"
    MINIO_USER="${MINIO_ROOT_USER:-goldpet-s3-access-key}"
    MINIO_PASS="${MINIO_ROOT_PASSWORD:-goldpet-s3-secret-key}"

    echo "  Registering mc alias '${MINIO_ALIAS}' -> ${MINIO_URL}"
    mc alias set "${MINIO_ALIAS}" "${MINIO_URL}" "${MINIO_USER}" "${MINIO_PASS}" --api S3v4

    echo "  Setting CORS..."
    mc cors set "${MINIO_ALIAS}/${PRIVATE_BUCKET}" "${CORS_FILE}"

    echo "  Verifying..."
    mc cors get "${MINIO_ALIAS}/${PRIVATE_BUCKET}"

    echo ""
    echo "==> Smoke test: presigned URL fetch"
    echo "    Run manually after enabling the feature flag:"
    echo "    PRESIGNED_URL=\$(curl -s http://localhost:8081/api/v1/walks/me/photos | jq -r '.content[0].imageUrl')"
    echo "    curl -v \"\$PRESIGNED_URL\" -o /dev/null"
    echo "    Expected: HTTP 200"
    ;;

  dev)
    MINIO_ALIAS="goldpet-dev"
    MINIO_URL="${MINIO_ENDPOINT:-http://127.0.0.1:9000}"
    MINIO_USER="${MINIO_ACCESS_KEY:?MINIO_ACCESS_KEY required}"
    MINIO_PASS="${MINIO_SECRET_KEY:?MINIO_SECRET_KEY required}"

    echo "  Registering mc alias '${MINIO_ALIAS}' -> ${MINIO_URL}"
    mc alias set "${MINIO_ALIAS}" "${MINIO_URL}" "${MINIO_USER}" "${MINIO_PASS}" --api S3v4

    echo "  Setting CORS..."
    mc cors set "${MINIO_ALIAS}/${PRIVATE_BUCKET}" "${CORS_FILE}"

    echo "  Verifying..."
    mc cors get "${MINIO_ALIAS}/${PRIVATE_BUCKET}"

    echo ""
    echo "==> Smoke test: presigned URL fetch from dev"
    echo "    Run manually after deploying the backend:"
    echo "    PRESIGNED_URL=\$(curl -s -H 'Authorization: Bearer <TOKEN>' https://api.mannamsquare.com/api/v1/walks/me/photos | jq -r '.content[0].imageUrl')"
    echo "    curl -v \"\$PRESIGNED_URL\" -o /dev/null"
    echo "    Expected: HTTP 200"
    ;;

  prod)
    AWS_REGION="${AWS_REGION:-ap-northeast-2}"
    AWS_BUCKET="${S3_PRIVATE_BUCKET_NAME:-goldpet-private}"

    echo "  Applying to AWS S3 bucket '${AWS_BUCKET}' in region '${AWS_REGION}'"
    echo "  NOTE: Ensure AWS credentials are configured (aws configure or IAM role)"

    aws s3api put-bucket-cors \
      --region "${AWS_REGION}" \
      --bucket "${AWS_BUCKET}" \
      --cors-configuration "file://${CORS_FILE}"

    echo "  Verifying..."
    aws s3api get-bucket-cors \
      --region "${AWS_REGION}" \
      --bucket "${AWS_BUCKET}"

    echo ""
    echo "==> Smoke test: presigned URL fetch from prod"
    echo "    Run manually after enabling the feature flag (admin console -> System Settings -> WALK_PHOTO_PRESIGNED_URL_ENABLED = true):"
    echo "    PRESIGNED_URL=\$(curl -s -H 'Authorization: Bearer <TOKEN>' https://api.goldpet.com/api/v1/walks/me/photos | jq -r '.content[0].imageUrl')"
    echo "    curl -v \"\$PRESIGNED_URL\" -o /dev/null"
    echo "    Expected: HTTP 200 with Access-Control-Allow-Origin header"
    ;;

  *)
    echo "ERROR: Unknown ENV='${ENV}'. Must be local, dev, or prod."
    exit 1
    ;;
esac

echo ""
echo "Done. CORS policy applied to '${PRIVATE_BUCKET}' (ENV=${ENV})."
