#!/bin/bash

# Frontend 배포 스크립트 (개발 서버용)
# Usage: ./deploy-dev/scripts/deploy-frontend.sh

set -e

DEPLOY_PATH="/home/dev/www/mannam/goldpet/frontend"

echo "🚀 Deploying GoldPet Frontend to dev server..."

# 빌드
cd "$(dirname "$0")/../../frontend"
npm ci
npm run build:dev

# 백업
if [ -d "$DEPLOY_PATH/dist" ]; then
    BACKUP_NAME="dist.backup.$(date +%Y%m%d_%H%M%S)"
    mv "$DEPLOY_PATH/dist" "$DEPLOY_PATH/$BACKUP_NAME"
    echo "📦 Backed up to $BACKUP_NAME"
fi

# 배포
cp -r dist "$DEPLOY_PATH/"

# Nginx 재로드
sudo systemctl reload nginx

echo "✅ Frontend deployed successfully!"
echo "   URL: https://app.mannamsquare.com"
