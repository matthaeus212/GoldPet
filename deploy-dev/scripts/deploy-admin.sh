#!/bin/bash

# Admin 배포 스크립트 (개발 서버용)
# Usage: ./deploy-dev/scripts/deploy-admin.sh

set -e

DEPLOY_PATH="/home/dev/www/mannam/goldpet/admin"

echo "🚀 Deploying GoldPet Admin to dev server..."

# 빌드
cd "$(dirname "$0")/../../admin"
npm ci
npm run build -- --mode dev

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

echo "✅ Admin deployed successfully!"
echo "   URL: https://admin.mannamsquare.com"
