#!/bin/bash

# API 서버 배포 스크립트 (개발 서버용)
# Usage: sudo ./deploy-dev/scripts/deploy-api.sh

set -e

DEPLOY_PATH="/home/dev/www/mannam/goldpet/api"
JAR_NAME="goldpet-api.jar"

echo "🚀 Deploying GoldPet API to dev server..."

# JAR 빌드
cd "$(dirname "$0")/../../api"
./gradlew clean bootJar

# 백업
if [ -f "$DEPLOY_PATH/$JAR_NAME" ]; then
    BACKUP_NAME="${JAR_NAME}.backup.$(date +%Y%m%d_%H%M%S)"
    cp "$DEPLOY_PATH/$JAR_NAME" "$DEPLOY_PATH/$BACKUP_NAME"
    echo "📦 Backed up to $BACKUP_NAME"
fi

# 서비스 중지
sudo systemctl stop goldpet-api || true

# JVM 완전 종료 대기 (최대 30초).
# systemctl stop 은 SIGTERM 만 보내고 리턴하므로 구 JVM 이 잠시 더 살아있을 수 있다.
# 살아있는 상태에서 JAR 를 교체하거나 다음 단계로 넘어가면 JDBC 세션이 Postgres 에 남아
# V57 같은 `CREATE INDEX CONCURRENTLY` 마이그레이션이 무한 대기한다.
echo "⏳ Waiting for old JVM to terminate..."
for _ in $(seq 1 30); do
    pgrep -f "$DEPLOY_PATH/$JAR_NAME" > /dev/null 2>&1 || break
    sleep 1
done
if pgrep -f "$DEPLOY_PATH/$JAR_NAME" > /dev/null 2>&1; then
    echo "⚠️  JVM did not terminate in 30s — forcing SIGKILL"
    sudo pkill -9 -f "$DEPLOY_PATH/$JAR_NAME" || true
    sleep 2
fi

# Postgres JDBC 잔류 세션 정리 (Flyway CONCURRENTLY hang 방지).
# 드물게 Postgres 쪽 TCP keepalive 가 늦게 감지돼 구 세션이 `idle in transaction` 으로 남으면
# 새 bootRun 의 Flyway `CREATE INDEX CONCURRENTLY` 가 자기 자신을 기다리다 무한 hang.
# 참고: V57__Chat_Performance_Indexes.sql 상단 runbook 주석과 동일 복구 절차.
echo "🧹 Terminating leftover PostgreSQL JDBC sessions..."
sudo docker exec -i goldpet-db sh <<'INNER_EOF' || echo "⚠️  JDBC cleanup skipped (non-fatal, e.g. DB container down)"
PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL_EOF'
SELECT pg_terminate_backend(pid), pid, application_name, state
  FROM pg_stat_activity
 WHERE datname = current_database()
   AND application_name = 'PostgreSQL JDBC Driver'
   AND pid <> pg_backend_pid();
SQL_EOF
INNER_EOF

# JAR 복사
cp build/libs/*.jar "$DEPLOY_PATH/$JAR_NAME"

# 서비스 시작
sudo systemctl start goldpet-api

echo "✅ API deployed successfully!"
echo "   Check status: sudo systemctl status goldpet-api"
