#!/bin/bash

# 로컬 개발 인프라 중지 스크립트
# Usage: ./deploy-local/scripts/stop.sh

set -e

echo "🛑 Stopping GoldPet Local Infrastructure..."

cd "$(dirname "$0")/.."
docker compose down

echo "✅ Local Infrastructure stopped."
