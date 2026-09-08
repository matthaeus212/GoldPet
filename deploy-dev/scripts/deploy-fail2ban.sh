#!/bin/bash

# fail2ban 설정 배포 스크립트 (개발 서버용)
# Usage: sudo ./deploy-dev/scripts/deploy-fail2ban.sh
#
# deploy-dev/fail2ban/ 의 filter.d / jail.d 파일을 /etc/fail2ban/ 에 동기화하고
# fail2ban-client -t 로 검증한 뒤 reload. 검증 실패 시 변경 적용 안 함.

set -e

SRC_DIR="$(dirname "$0")/../fail2ban"
DEST_DIR="/etc/fail2ban"

echo "🛡️  Deploying fail2ban configs to dev server..."

# 1) filter.d 파일 동기화
echo "📋 Syncing filter.d/..."
sudo install -m 0644 -o root -g root \
    "$SRC_DIR/filter.d/nginx-actuator-scan.conf" \
    "$DEST_DIR/filter.d/nginx-actuator-scan.conf"

# 2) jail.d 파일 동기화
echo "📋 Syncing jail.d/..."
sudo install -m 0644 -o root -g root \
    "$SRC_DIR/jail.d/nginx-goldpet.conf" \
    "$DEST_DIR/jail.d/nginx-goldpet.conf"

# 3) 설정 검증 (실패 시 set -e 로 중단 — 잘못된 설정으로 reload 하면 fail2ban 죽음)
echo "🔍 Validating fail2ban config..."
sudo fail2ban-client -t

# 4) reload (서비스 재시작 아님 — 진행 중인 ban 유지)
echo "♻️  Reloading fail2ban..."
sudo systemctl reload fail2ban

# 5) 활성 jail 출력
sleep 2
echo ""
echo "✅ Deployed. 활성 jail:"
sudo fail2ban-client status
