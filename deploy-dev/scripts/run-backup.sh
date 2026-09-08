#!/bin/bash
set -euo pipefail

# Phase 3: backup_jobs 큐에서 PENDING pickup → pg_dump → 로컬 디스크 저장 → status 갱신
#
# 호출 패턴:
# 1) systemd timer 가 매일 03시 호출 (auto cron 모드: PENDING 없으면 새 row INSERT)
# 2) goldpet-backup-watcher.timer 가 1분 간격으로 PENDING 감시 (수동 trigger 신속 처리)
#
# 동시성 안전: SKIP LOCKED 로 여러 timer 가 동시에 실행돼도 중복 pickup 없음.
# 보존: BACKUP_RETENTION_DAYS 일 이상 된 파일 자동 삭제 (default 30).
#
# OPS-001 오프사이트 복제:
#   백업이 원본 DB 와 같은 디스크에만 있으면 서버/디스크 장애 시 DB 와 백업이 함께 사라진다.
#   BACKUP_S3_BUCKET 이 설정되면 덤프를 S3 로 복제한다. 업로드가 실패하면 유닛을 실패시켜
#   (exit 1) 조용히 새지 않게 한다 — 로컬 덤프 자체는 이미 SUCCEEDED 로 기록된 뒤다.
#   자격증명/버킷은 /etc/goldpet/backup.env 에만 두고 리포지토리에는 두지 않는다.
#
# Usage:
#   DB_HOST=localhost DB_PORT=5432 DB_NAME=goldpet_db DB_USER=goldpet \
#   PGPASSWORD=secret BACKUP_DIR=/var/backups/goldpet ./run-backup.sh
#
# 환경변수는 /etc/goldpet/backup.env 에서 EnvironmentFile 로 주입됨.

###############################################################################
# Configuration
###############################################################################
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-goldpet_db}"
DB_USER="${DB_USER:-goldpet}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/goldpet}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

# OPS-001: 오프사이트 복제 대상 (미설정이면 복제를 건너뛴다 — 기존 동작과 동일)
BACKUP_S3_BUCKET="${BACKUP_S3_BUCKET:-}"
BACKUP_S3_PREFIX="${BACKUP_S3_PREFIX:-goldpet-backups}"

# PGPASSWORD 는 backup.env 의 DB_PASSWORD 에서 주입; 미설정 시 .pgpass 폴백
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-}}"

PSQL="psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -At -v ON_ERROR_STOP=1"

###############################################################################
# Helpers
###############################################################################
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

###############################################################################
# 0. BACKUP_DIR 보장
###############################################################################
mkdir -p "$BACKUP_DIR"
chmod 0750 "$BACKUP_DIR" 2>/dev/null || true

###############################################################################
# 1. PENDING pickup (RUNNING 으로 원자 전환 — SKIP LOCKED 으로 race-safe)
###############################################################################
JOB_ID=$($PSQL -c "
    UPDATE backup_jobs SET status='RUNNING', started_at=NOW()
    WHERE id = (SELECT id FROM backup_jobs WHERE status='PENDING' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED)
    RETURNING id;
" 2>/dev/null | grep -E '^[0-9]+$' | head -n1) || true

if [ -z "$JOB_ID" ]; then
    # PENDING 없을 때 동작은 BACKUP_MODE 에 따라 분기
    if [ "${BACKUP_MODE:-pickup}" = "auto" ]; then
        # daily timer (03시) — 신규 AUTO_CRON job INSERT 후 즉시 진행
        log "No PENDING job found — creating AUTO_CRON backup job..."
        JOB_ID=$($PSQL -c "
            INSERT INTO backup_jobs (status, trigger_type, started_at, created_at)
            VALUES ('RUNNING', 'AUTO_CRON', NOW(), NOW())
            RETURNING id;
        " 2>/dev/null | grep -E '^[0-9]+$' | head -n1)
    else
        # watcher (pickup-only) — PENDING 없으면 조용히 종료
        log "No PENDING job — pickup mode, exiting"
        exit 0
    fi
fi

if [ -z "$JOB_ID" ]; then
    log "ERROR: Failed to obtain JOB_ID after pickup/insert"
    exit 1
fi

log "Picked up backup job: id=${JOB_ID}"

###############################################################################
# 2. pg_dump → gzip → BACKUP_DIR
###############################################################################
DATE=$(date +%Y-%m-%d-%H%M%S)
DUMP_FILE="${BACKUP_DIR}/goldpet-${DATE}.sql.gz"

log "Starting pg_dump → $DUMP_FILE"
if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -F p "$DB_NAME" | gzip > "$DUMP_FILE"; then
    SIZE=$(stat -c%s "$DUMP_FILE" 2>/dev/null || stat -f%z "$DUMP_FILE")

    $PSQL -c "
        UPDATE backup_jobs
        SET status='SUCCEEDED', finished_at=NOW(),
            file_path='${DUMP_FILE}',
            file_size_bytes=${SIZE}
        WHERE id=${JOB_ID};
    "
    log "Backup ${JOB_ID} succeeded: ${DUMP_FILE} (${SIZE} bytes)"
else
    rm -f "$DUMP_FILE"
    log "ERROR: pg_dump failed for job ${JOB_ID}"
    $PSQL -c "
        UPDATE backup_jobs
        SET status='FAILED', finished_at=NOW(), error_message='pg_dump failed'
        WHERE id=${JOB_ID};
    "
    exit 1
fi

###############################################################################
# 3. OPS-001: 오프사이트 복제 (S3)
###############################################################################
if [ -n "$BACKUP_S3_BUCKET" ]; then
    # 업로드 전에 아카이브 무결성을 확인한다. 깨진 덤프를 오프사이트에 올려두면
    # 정작 복구가 필요한 순간에 쓸 수 없다.
    if ! gzip -t "$DUMP_FILE"; then
        log "ERROR: dump integrity check failed (gzip -t) — skipping offsite upload: $DUMP_FILE"
        $PSQL -c "
            UPDATE backup_jobs SET error_message='dump corrupt — offsite skipped'
            WHERE id=${JOB_ID};
        " || true
        exit 1
    fi

    S3_URI="s3://${BACKUP_S3_BUCKET}/${BACKUP_S3_PREFIX}/$(basename "$DUMP_FILE")"
    log "Replicating offsite → $S3_URI"

    if aws s3 cp "$DUMP_FILE" "$S3_URI" --only-show-errors; then
        # 업로드가 실제로 도착했고 크기가 같은지 확인한다(조용한 절단 방지).
        REMOTE_SIZE=$(aws s3api head-object \
            --bucket "$BACKUP_S3_BUCKET" \
            --key "${BACKUP_S3_PREFIX}/$(basename "$DUMP_FILE")" \
            --query ContentLength --output text 2>/dev/null || echo "")
        if [ "$REMOTE_SIZE" != "$SIZE" ]; then
            log "ERROR: offsite size mismatch (local=${SIZE}, remote=${REMOTE_SIZE:-none})"
            $PSQL -c "
                UPDATE backup_jobs SET error_message='offsite size mismatch'
                WHERE id=${JOB_ID};
            " || true
            exit 1
        fi
        log "Offsite replication verified: ${S3_URI} (${SIZE} bytes)"
    else
        log "ERROR: offsite upload failed for job ${JOB_ID} → ${S3_URI}"
        $PSQL -c "
            UPDATE backup_jobs SET error_message='offsite upload failed'
            WHERE id=${JOB_ID};
        " || true
        exit 1
    fi
else
    log "WARN: BACKUP_S3_BUCKET 미설정 — 오프사이트 복제 없음 (로컬 디스크에만 백업됨)"
fi

###############################################################################
# 4. 보존 정책: BACKUP_RETENTION_DAYS 일 이상된 파일 삭제
###############################################################################
DELETED=$(find "$BACKUP_DIR" -name 'goldpet-*.sql.gz' -mtime +"${BACKUP_RETENTION_DAYS}" -delete -print | wc -l)
if [ "$DELETED" -gt 0 ]; then
    log "Pruned ${DELETED} backup file(s) older than ${BACKUP_RETENTION_DAYS} days"
fi
