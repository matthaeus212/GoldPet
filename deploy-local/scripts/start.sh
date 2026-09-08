#!/bin/bash

# 로컬 개발 인프라 시작 스크립트
# Usage: ./deploy-local/scripts/start.sh

set -e

echo "🚀 Starting GoldPet Local Infrastructure..."

# 프로젝트 루트로 이동
cd "$(dirname "$0")/../.."

# Docker 실행 확인
if ! docker info > /dev/null 2>&1; then
  echo "❌ Error: Docker is not running. Please start Docker Desktop."
  exit 1
fi

# Docker Compose 실행
cd deploy-local
docker compose up -d

echo ""
echo "✅ Local Infrastructure is UP!"
echo ""
echo "   PostgreSQL : localhost:5433 (goldpet / goldpet123)"
echo "   Redis      : localhost:6379 (password: goldpet123)"
echo "   MinIO API  : localhost:9100"
echo "   MinIO Console: http://localhost:9101"
echo ""
echo "👉 Now run: cd api && ./gradlew bootRun"
