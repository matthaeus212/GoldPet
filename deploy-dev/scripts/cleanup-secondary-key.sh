#!/bin/bash

# PII 암호화 키 교체 후 정리 스크립트 (개발 서버용)
# Usage: ADMIN_JWT=<bearer-token> ./cleanup-secondary-key.sh
#
# Run this script ON the dev server 24 hours after rotate-encryption-key.sh completes.
# Removes ENCRYPTION_KEY_SECONDARY and legacy ENCRYPTION_KEY from the env file.
# This is the point of no return — after this, only the new primary key can decrypt PII.
#
# See plan: .omc/plans/dev-encryption-key-rotation.md §6

set -euo pipefail

###############################################################################
# Configuration
###############################################################################
ENV_FILE="/home/dev/www/mannam/goldpet/api/goldpet-api.env"
SERVICE="goldpet-api"
API_BASE="http://localhost:8081"
HEALTH_TIMEOUT=60

###############################################################################
# Helpers
###############################################################################
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }
fail() { echo "❌ ERROR: $*" >&2; exit 1; }

###############################################################################
# Validate inputs
###############################################################################
: "${ADMIN_JWT:?ADMIN_JWT env var is required (SUPER_ADMIN bearer token)}"

[[ -f "$ENV_FILE" ]] || fail "Env file not found: $ENV_FILE"

###############################################################################
# 1. Assert rotation is complete — dry-run must return rotated:0
###############################################################################
log "Verifying rotation is complete (dry-run)..."
VERIFY_RESPONSE=$(curl -sf -X POST \
    -H "Authorization: Bearer ${ADMIN_JWT}" \
    -H "Content-Type: application/json" \
    "${API_BASE}/api/admin/migration/rotate-encryption-key/dry-run")
log "Dry-run result: ${VERIFY_RESPONSE}"

ROTATED_COUNT=$(echo "$VERIFY_RESPONSE" | grep -o '"rotated":[0-9]*' | cut -d: -f2 || echo "-1")
FAILED_COUNT=$(echo "$VERIFY_RESPONSE" | grep -o '"failed":[0-9]*' | cut -d: -f2 || echo "-1")

[[ "$ROTATED_COUNT" == "0" ]] || fail "Dry-run shows rotated=$ROTATED_COUNT (expected 0). Rotation is not complete — run rotate-encryption-key.sh first."
[[ "$FAILED_COUNT" == "0" ]] || fail "Dry-run shows failed=$FAILED_COUNT (expected 0). Fix errors before removing secondary key."

ALREADY_NEW=$(echo "$VERIFY_RESPONSE" | grep -o '"alreadyNew":[0-9]*' | cut -d: -f2 || echo "?")
log "✅ Rotation confirmed complete (rotated=0, alreadyNew=${ALREADY_NEW})"

###############################################################################
# 2. Remove ENCRYPTION_KEY_SECONDARY and legacy ENCRYPTION_KEY from env file
###############################################################################
# Verify SECONDARY is actually present before claiming to remove it
CURRENT_SECONDARY=$(grep '^ENCRYPTION_KEY_SECONDARY=' "$ENV_FILE" | head -1 | cut -d'=' -f2- || true)
[[ -n "$CURRENT_SECONDARY" ]] || fail "ENCRYPTION_KEY_SECONDARY not found in $ENV_FILE — already removed?"

log "Removing ENCRYPTION_KEY_SECONDARY and legacy ENCRYPTION_KEY from $ENV_FILE..."
TEMP_ENV=$(mktemp "${ENV_FILE}.XXXXXX")
grep -v '^ENCRYPTION_KEY_SECONDARY=' "$ENV_FILE" \
    | grep -v '^ENCRYPTION_KEY=' \
    > "$TEMP_ENV"
mv "$TEMP_ENV" "$ENV_FILE"
log "✅ Secondary key and legacy ENCRYPTION_KEY removed from env file"

###############################################################################
# 3. Restart service
###############################################################################
log "Restarting $SERVICE..."
sudo systemctl restart "$SERVICE"

###############################################################################
# 4. Health check
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
# 5. Final dry-run — must still show rotated:0 with primary key only
###############################################################################
log "Running final verification dry-run (primary key only now active)..."
FINAL_RESPONSE=$(curl -sf -X POST \
    -H "Authorization: Bearer ${ADMIN_JWT}" \
    -H "Content-Type: application/json" \
    "${API_BASE}/api/admin/migration/rotate-encryption-key/dry-run")
log "Final dry-run: ${FINAL_RESPONSE}"

FINAL_ROTATED=$(echo "$FINAL_RESPONSE" | grep -o '"rotated":[0-9]*' | cut -d: -f2 || echo "-1")
FINAL_FAILED=$(echo "$FINAL_RESPONSE" | grep -o '"failed":[0-9]*' | cut -d: -f2 || echo "-1")

[[ "$FINAL_ROTATED" == "0" ]] || fail "Final dry-run shows rotated=$FINAL_ROTATED — unexpected rows not under primary key"
[[ "$FINAL_FAILED" == "0" ]] || fail "Final dry-run shows failed=$FINAL_FAILED — decrypt errors with primary-only config"
log "✅ Final verification passed (rotated=0, failed=0)"

echo ""
echo "======================================================================"
echo " ✅  Secondary key cleanup complete."
echo "     Only ENCRYPTION_KEY_PRIMARY is now active."
echo ""
echo " ⚠️   POST-CLEANUP CHECKLIST:"
echo "     1. Have a designated tester perform a Kakao OAuth2 login via TestFlight"
echo "     2. Confirm same user id returned (no new account created)"
echo "     3. Update MEMORY.md with rotation completion entry"
echo "     4. Schedule CSV/SQL backup deletion in 30 days: $ENV_FILE region"
echo "     5. File follow-up tickets (see plan §11)"
echo "======================================================================"
