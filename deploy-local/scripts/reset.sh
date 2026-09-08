#!/bin/bash

# 로컬 개발 인프라 초기화 스크립트 (데이터 삭제)
# Usage: ./deploy-local/scripts/reset.sh

set -e

echo "🔄 Resetting GoldPet Local Infrastructure (deleting all data)..."

cd "$(dirname "$0")/.."

# 볼륨 포함 삭제
docker compose down -v

# 다시 시작
docker compose up -d

echo ""
echo "✅ Local Infrastructure reset complete!"
echo "   All data has been deleted and services restarted."
