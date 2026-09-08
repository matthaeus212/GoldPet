#!/bin/bash

# PII 암호화 키 교체 스크립트 (개발 서버용)
# Usage: OLD_KEY=<32-char> NEW_KEY=<32-char> ADMIN_JWT=<bearer-token> ./rotate-encryption-key.sh [--dry-run]
#
# Run this script ON the dev server after SSH-ing in.
# Prereqs (human checklist before running):
#   1. NEW_KEY stored in 1Password (vault: GoldPet - Dev Infrastructure,
#      entry: ENCRYPTION_KEY_dev_rotation-<date>, tag: rotation-<date>)
#   2. OLD_KEY tagged as retired in 1Password
#   3. Two-person acknowledgment recorded (Slack/KakaoTalk timestamp)
#   4. T-24h tester notification sent to KakaoTalk "GoldPet TestFlight" group
#
# See plan: .omc/plans/dev-encryption-key-rotation.md §6

set -euo pipefail

###############################################################################
# Configuration
###############################################################################
ENV_FILE="/home/dev/www/mannam/goldpet/api/goldpet-api.env"
SERVICE="goldpet-api"
BACKUP_DIR="/home/dev/backups"
API_BASE="http://localhost:8081"
DB_CONTAINER="goldpet-db"
DB_USER="goldpet"
DB_NAME="goldpet_db"
HEALTH_TIMEOUT=60

###############################################################################
# Argument parsing
###############################################################################
DRY_RUN=false
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        *) echo "Unknown argument: $arg" >&2; exit 1 ;;
    esac
done

###############################################################################
# Helpers
###############################################################################
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }
fail() { echo "❌ ERROR: $*" >&2; exit 1; }

byte_count() { printf '%s' "$1" | wc -c | tr -d ' '; }

###############################################################################
# 1. Validate inputs
###############################################################################
: "${OLD_KEY:?OLD_KEY env var is required (32-byte current key)}"
: "${NEW_KEY:?NEW_KEY env var is required (32-byte new key)}"
: "${ADMIN_JWT:?ADMIN_JWT env var is required (SUPER_ADMIN bearer token)}"

OLD_KEY_LEN=$(byte_count "$OLD_KEY")
NEW_KEY_LEN=$(byte_count "$NEW_KEY")

[[ "$OLD_KEY_LEN" -eq 32 ]] || fail "OLD_KEY must be exactly 32 bytes (got $OLD_KEY_LEN)"
[[ "$NEW_KEY_LEN" -eq 32 ]] || fail "NEW_KEY must be exactly 32 bytes (got $NEW_KEY_LEN)"
[[ "$OLD_KEY" != "$NEW_KEY" ]] || fail "OLD_KEY and NEW_KEY must differ"

log "✅ Key lengths validated (both 32 bytes)"

###############################################################################
# 2. Assert current ENCRYPTION_KEY in env file matches OLD_KEY (sanity)
###############################################################################
[[ -f "$ENV_FILE" ]] || fail "Env file not found: $ENV_FILE"

CURRENT_KEY=$(grep '^ENCRYPTION_KEY=' "$ENV_FILE" | head -1 | cut -d'=' -f2- || true)
[[ "$CURRENT_KEY" == "$OLD_KEY" ]] || fail "ENCRYPTION_KEY in $ENV_FILE does not match OLD_KEY — refusing to proceed"

log "✅ Current ENCRYPTION_KEY matches OLD_KEY"

# Scenario 2 mitigation: refuse if PRIMARY already differs from OLD_KEY but SECONDARY is unset
# (this is the dangerous half-rotated state that causes email_hash drift)
CURRENT_PRIMARY=$(grep '^ENCRYPTION_KEY_PRIMARY=' "$ENV_FILE" | head -1 | cut -d'=' -f2- || true)
CURRENT_SECONDARY=$(grep '^ENCRYPTION_KEY_SECONDARY=' "$ENV_FILE" | head -1 | cut -d'=' -f2- || true)

if [[ -n "$CURRENT_PRIMARY" && "$CURRENT_PRIMARY" != "$OLD_KEY" && -z "$CURRENT_SECONDARY" ]]; then
    fail "ENCRYPTION_KEY_PRIMARY already differs from OLD_KEY but ENCRYPTION_KEY_SECONDARY is unset. " \
         "This is the Scenario 2 (email_hash drift) configuration. " \
         "Set ENCRYPTION_KEY_SECONDARY=<old_key> before re-running, or restore the env file."
fi

if "$DRY_RUN"; then
    log "🔍 DRY RUN MODE — no writes will be performed"
fi

###############################################################################
# 3. Ensure backup directory exists with 700 permissions (M3)
###############################################################################
log "Checking backup directory: $BACKUP_DIR"
if [[ ! -d "$BACKUP_DIR" ]]; then
    if "$DRY_RUN"; then
        log "🔍 [DRY RUN] Would create $BACKUP_DIR with 700 permissions"
    else
        mkdir -p "$BACKUP_DIR"
        chmod 700 "$BACKUP_DIR"
        log "✅ Created $BACKUP_DIR (700)"
    fi
else
    BACKUP_PERMS=$(stat -c '%a' "$BACKUP_DIR")
    [[ "$BACKUP_PERMS" == "700" ]] || fail "$BACKUP_DIR exists but perms are $BACKUP_PERMS (expected 700)"
    log "✅ $BACKUP_DIR exists with correct permissions"
fi

TIMESTAMP=$(date '+%Y%m%d-%H%M%S')
DUMP_FILE="$BACKUP_DIR/users-${TIMESTAMP}.sql"
CSV_FILE="$BACKUP_DIR/users-${TIMESTAMP}.csv"

###############################################################################
# 4. pg_dump users table — primary rollback artifact
###############################################################################
if "$DRY_RUN"; then
    log "🔍 [DRY RUN] Would pg_dump users table → $DUMP_FILE"
else
    log "Taking pg_dump of users table → $DUMP_FILE"
    docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" -t users --data-only > "$DUMP_FILE"
    log "✅ pg_dump complete: $DUMP_FILE"
fi

###############################################################################
# 5. CSV export of PII columns — for targeted column-UPDATE rollback (M4)
###############################################################################
if "$DRY_RUN"; then
    log "🔍 [DRY RUN] Would export PII columns CSV → $CSV_FILE"
else
    log "Exporting PII columns to CSV → $CSV_FILE"
    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
        -c "\copy (SELECT id, email, name, phone_number, birth_date, email_hash FROM users) TO STDOUT WITH CSV HEADER" \
        > "$CSV_FILE"
    CSV_ROW_COUNT=$(( $(wc -l < "$CSV_FILE") - 1 ))
    log "✅ CSV export complete: $CSV_FILE ($CSV_ROW_COUNT rows)"
fi

###############################################################################
# 6. Write new env file atomically (temp file + mv)
#    - ENCRYPTION_KEY_PRIMARY = NEW_KEY
#    - ENCRYPTION_KEY_SECONDARY = OLD_KEY
#    - ENCRYPTION_KEY stays as OLD_KEY (legacy readers)
###############################################################################
if "$DRY_RUN"; then
    log "🔍 [DRY RUN] Would update $ENV_FILE:"
    log "         + ENCRYPTION_KEY_PRIMARY=${NEW_KEY}"
    log "         + ENCRYPTION_KEY_SECONDARY=${OLD_KEY}"
    log "           ENCRYPTION_KEY=${OLD_KEY}  (unchanged — legacy)"
else
    TEMP_ENV=$(mktemp "${ENV_FILE}.XXXXXX")
    # Remove any stale PRIMARY/SECONDARY lines, keep all other env vars
    grep -v '^ENCRYPTION_KEY_PRIMARY=' "$ENV_FILE" \
        | grep -v '^ENCRYPTION_KEY_SECONDARY=' \
        > "$TEMP_ENV"
    echo "ENCRYPTION_KEY_PRIMARY=${NEW_KEY}" >> "$TEMP_ENV"
    echo "ENCRYPTION_KEY_SECONDARY=${OLD_KEY}" >> "$TEMP_ENV"
    mv "$TEMP_ENV" "$ENV_FILE"
    log "✅ Env file updated atomically"

    ###############################################################################
    # 7. Validate env write (M3): key on disk must be exactly 32 bytes
    ###############################################################################
    WRITTEN_LEN=$(grep '^ENCRYPTION_KEY_PRIMARY=' "$ENV_FILE" | cut -d'=' -f2- | tr -d '\n' | wc -c | tr -d ' ')
    [[ "$WRITTEN_LEN" -eq 32 ]] || fail "Post-write validation failed: ENCRYPTION_KEY_PRIMARY on disk is $WRITTEN_LEN bytes (expected 32). Check $ENV_FILE immediately."
    log "✅ Post-write validation passed (ENCRYPTION_KEY_PRIMARY is 32 bytes on disk)"

    ###############################################################################
    # 8. Restart service
    ###############################################################################
    log "Restarting $SERVICE..."
    sudo systemctl restart "$SERVICE"

    ###############################################################################
    # 9. Poll health endpoint until UP or timeout
    ###############################################################################
    log "Polling ${API_BASE}/actuator/health (timeout: ${HEALTH_TIMEOUT}s)..."
    ELAPSED=0
    until curl -sf "${API_BASE}/actuator/health" | grep -q '"status":"UP"'; do
        sleep 2
        ELAPSED=$(( ELAPSED + 2 ))
        printf '.'
        [[ "$ELAPSED" -lt "$HEALTH_TIMEOUT" ]] || fail "Health check timed out after ${HEALTH_TIMEOUT}s — check: sudo journalctl -u $SERVICE -n 50"
    done
    echo ""
    log "✅ Service is UP"

    ###############################################################################
    # 10. Dry-run endpoint — preview classification, require human confirmation
    ###############################################################################
    log "Calling dry-run rotation endpoint (preview)..."
    DRYRUN_RESPONSE=$(curl -sf -X POST \
        -H "Authorization: Bearer ${ADMIN_JWT}" \
        -H "Content-Type: application/json" \
        "${API_BASE}/api/admin/migration/rotate-encryption-key/dry-run")
    log "Dry-run result: ${DRYRUN_RESPONSE}"

    echo ""
    echo "======================================================================"
    echo " Dry-run classification shown above."
    echo " Type 'y' to run the actual rotation, any other key to abort."
    echo "======================================================================"
    read -r CONFIRM
    [[ "$CONFIRM" == "y" ]] || fail "Rotation aborted by operator."

    ###############################################################################
    # 11. Run rotation endpoint — fail if HTTP != 200, failed > 0, or lockContended
    ###############################################################################
    log "Running rotation endpoint..."
    HTTP_STATUS=$(curl -s -o /tmp/goldpet_rotation_response.json -w "%{http_code}" \
        -X POST \
        -H "Authorization: Bearer ${ADMIN_JWT}" \
        -H "Content-Type: application/json" \
        "${API_BASE}/api/admin/migration/rotate-encryption-key")
    ROTATION_RESPONSE=$(cat /tmp/goldpet_rotation_response.json)
    log "Rotation response (HTTP ${HTTP_STATUS}): ${ROTATION_RESPONSE}"

    [[ "$HTTP_STATUS" == "200" ]] || fail "Rotation endpoint returned HTTP $HTTP_STATUS (expected 200). Response: ${ROTATION_RESPONSE}"

    FAILED_COUNT=$(echo "$ROTATION_RESPONSE" | grep -o '"failed":[0-9]*' | cut -d: -f2 || echo "0")
    LOCK_CONTENDED=$(echo "$ROTATION_RESPONSE" | grep -o '"lockContended":true' || true)

    [[ "$FAILED_COUNT" == "0" ]] || fail "Rotation reported $FAILED_COUNT failed rows. ENCRYPTION_KEY_SECONDARY is still set for fallback reads — fix errors and re-run."
    [[ -z "$LOCK_CONTENDED" ]] || fail "Rotation returned lockContended=true — another rotation may be in progress."
    log "✅ Rotation complete"

    ###############################################################################
    # 12. Post-rotation dry-run — must return rotated:0 to confirm convergence
    ###############################################################################
    log "Running post-rotation dry-run to verify convergence..."
    VERIFY_RESPONSE=$(curl -sf -X POST \
        -H "Authorization: Bearer ${ADMIN_JWT}" \
        -H "Content-Type: application/json" \
        "${API_BASE}/api/admin/migration/rotate-encryption-key/dry-run")
    log "Post-rotation dry-run: ${VERIFY_RESPONSE}"

    ROTATED_COUNT=$(echo "$VERIFY_RESPONSE" | grep -o '"rotated":[0-9]*' | cut -d: -f2 || echo "-1")
    [[ "$ROTATED_COUNT" == "0" ]] || fail "Post-rotation dry-run shows rotated=$ROTATED_COUNT (expected 0). Rotation may be incomplete — re-run or restore from CSV backup: $CSV_FILE"
    log "✅ Convergence confirmed (rotated=0)"

    ###############################################################################
    # 13. Summary and next-step reminder
    ###############################################################################
    echo ""
    echo "======================================================================"
    echo " ✅  Key rotation complete."
    echo "     Backup SQL : $DUMP_FILE"
    echo "     Backup CSV : $CSV_FILE"
    echo "     Retain both files for 30 days (see plan §11)."
    echo ""
    echo " ⚠️   NEXT STEP: 24-hour soak, then run cleanup-secondary-key.sh"
    echo "     ADMIN_JWT=\$ADMIN_JWT ./deploy-dev/scripts/cleanup-secondary-key.sh"
    echo "======================================================================"
fi
