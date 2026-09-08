# GoldPet DB 백업 운영 가이드 (Phase 3)

## 개요

`run-backup.sh` 는 PostgreSQL 전체 덤프를 S3 에 업로드하고 `backup_jobs` 테이블로 상태를 추적합니다.

- **자동 백업**: `goldpet-backup.timer` — 매일 03:00 (Asia/Seoul)
- **수동 trigger 처리**: `goldpet-backup-watcher.timer` — 부팅 2분 후부터 1분 주기로 PENDING row 감시
- **동시성 안전**: `SELECT ... FOR UPDATE SKIP LOCKED` 로 중복 실행 방지

---

## 1. 설치 절차

### 1-1. 스크립트 배포

```bash
# 스크립트 배포 디렉터리 생성 및 복사
sudo mkdir -p /opt/goldpet/scripts
sudo cp deploy-dev/scripts/run-backup.sh /opt/goldpet/scripts/
sudo chmod +x /opt/goldpet/scripts/run-backup.sh
```

### 1-2. 환경 변수 파일 생성

```bash
sudo mkdir -p /etc/goldpet
sudo tee /etc/goldpet/backup.env > /dev/null <<'EOF'
# PostgreSQL 접속 정보
DB_HOST=localhost
DB_PORT=5432
DB_NAME=goldpet_db
DB_USER=goldpet
PGPASSWORD=<실제_DB_비밀번호>

# S3 / MinIO 설정
S3_BUCKET=goldpet-backups-dev

# AWS 자격증명 (AWS S3 사용 시)
AWS_DEFAULT_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=<ACCESS_KEY>
AWS_SECRET_ACCESS_KEY=<SECRET_KEY>

# MinIO 사용 시 — AWS CLI 가 endpoint-url 환경변수를 자동 인식 (v2.13+)
# AWS_ENDPOINT_URL=http://localhost:9000
EOF

# 소유권 및 권한 제한 (root만 읽기)
sudo chown root:root /etc/goldpet/backup.env
sudo chmod 600 /etc/goldpet/backup.env
```

### 1-3. systemd 유닛 배포

```bash
# 4개 유닛 파일 복사
sudo cp deploy-dev/systemd/services/goldpet-backup.service  /etc/systemd/system/
sudo cp deploy-dev/systemd/services/goldpet-backup.timer    /etc/systemd/system/
sudo cp deploy-dev/systemd/services/goldpet-backup-watcher.timer /etc/systemd/system/

# daemon reload 후 활성화
sudo systemctl daemon-reload
sudo systemctl enable --now goldpet-backup.timer goldpet-backup-watcher.timer
```

---

## 2. 환경 변수 레퍼런스

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `DB_HOST` | `localhost` | PostgreSQL 호스트 |
| `DB_PORT` | `5432` | PostgreSQL 포트 |
| `DB_NAME` | `goldpet_db` | 데이터베이스 이름 |
| `DB_USER` | `goldpet` | 데이터베이스 사용자 |
| `PGPASSWORD` | (없음) | pg_dump 인증 비밀번호; 미설정 시 .pgpass 폴백 |
| `S3_BUCKET` | `goldpet-backups-dev` | 덤프 파일 업로드 대상 S3 버킷 |
| `AWS_DEFAULT_REGION` | — | AWS 리전 (AWS S3 사용 시 필수) |
| `AWS_ACCESS_KEY_ID` | — | AWS 액세스 키 |
| `AWS_SECRET_ACCESS_KEY` | — | AWS 시크릿 키 |
| `AWS_ENDPOINT_URL` | — | MinIO 사용 시 엔드포인트 (예: `http://localhost:9000`) |

---

## 3. 동작 상태 확인

```bash
# 타이머 상태 조회
sudo systemctl status goldpet-backup.timer goldpet-backup-watcher.timer

# 다음 실행 시각 확인
sudo systemctl list-timers goldpet-backup*

# 마지막 실행 로그
sudo journalctl -u goldpet-backup.service -n 50 --no-pager

# backup_jobs 테이블 최근 10건 조회
PGPASSWORD=<비밀번호> psql -h localhost -U goldpet -d goldpet_db \
  -c "SELECT id, status, trigger_type, started_at, finished_at, file_size_bytes, error_message FROM backup_jobs ORDER BY created_at DESC LIMIT 10;"
```

---

## 4. 수동 실행 (테스트)

```bash
# systemd 서비스 직접 기동 (수동 트리거)
sudo systemctl start goldpet-backup.service

# 또는 스크립트 직접 실행 (환경변수 설정 필요)
sudo -u goldpet \
  DB_NAME=goldpet_db PGPASSWORD=<비밀번호> S3_BUCKET=goldpet-backups-dev \
  /opt/goldpet/scripts/run-backup.sh
```

---

## 5. S3 Lifecycle 정책 권고

백업 버킷 `goldpet-backups-dev` 에 아래 lifecycle 정책을 적용하여 오래된 덤프를 자동 삭제:

```json
{
  "Rules": [
    {
      "ID": "goldpet-backup-retention-30d",
      "Status": "Enabled",
      "Filter": { "Prefix": "" },
      "Expiration": { "Days": 30 }
    }
  ]
}
```

적용 명령:
```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket goldpet-backups-dev \
  --lifecycle-configuration file://lifecycle-30d.json
```

> **권고**: dev 환경은 30일 보존. prod 환경은 규정 요건에 따라 90일 이상 권장.

---

## 6. 복원 (Restore) 절차

```bash
# 1. S3 에서 덤프 다운로드
aws s3 cp "s3://goldpet-backups-dev/<YYYY-MM-DD-HHmmSS>/dump.sql.gz" /tmp/restore.sql.gz

# 2. 압축 해제
gzip -d /tmp/restore.sql.gz

# 3. DB 복원 (주의: 기존 데이터 덮어쓰기)
PGPASSWORD=<비밀번호> psql -h localhost -U goldpet -d goldpet_db < /tmp/restore.sql

# 4. 복원 검증
PGPASSWORD=<비밀번호> psql -h localhost -U goldpet -d goldpet_db \
  -c "SELECT count(*) FROM users;"
```

> ⚠️ 복원 전 현재 DB 를 별도 덤프로 보존할 것.

---

## 7. 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| `PGPASSWORD: unbound variable` | backup.env 에 PGPASSWORD 누락 | `/etc/goldpet/backup.env` 에 `PGPASSWORD=` 추가 |
| `pg_dump: not found` | pg_dump 미설치 | `sudo apt install postgresql-client-16` |
| `aws: not found` | AWS CLI 미설치 | `sudo apt install awscli` 또는 AWS CLI v2 설치 |
| S3 업로드 실패 | 자격증명 오류 | backup.env 의 `AWS_ACCESS_KEY_ID`/`SECRET` 확인 |
| `backup_jobs` 테이블 없음 | W1 마이그레이션(V62) 미적용 | API 서버 재시작 후 Flyway 마이그레이션 확인 |
| 잡이 RUNNING 상태로 멈춤 | 이전 실행 비정상 종료 | `UPDATE backup_jobs SET status='FAILED' WHERE status='RUNNING';` |
