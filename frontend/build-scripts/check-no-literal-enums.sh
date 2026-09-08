#!/usr/bin/env bash
# Phase 4.2 lint: detect hand-written domain enum unions outside src/api/.
#
# Each generated enum should be re-exported via types/api.ts (which derives from
# Schemas[...] in the OpenAPI-generated schema.d.ts). Manual literal unions for
# known domain enums silently drift from the backend schema and re-introduce the
# bug class Phase 3 was built to fix (c2eb8ca, etc.).
#
# Run from repo root.

set -euo pipefail

# Run from repo root regardless of caller cwd (so git grep paths resolve).
cd "$(git rev-parse --show-toplevel)"

# Domain enum names — keep in sync with frontend/admin types/api.ts re-exports.
DOMAIN_ENUMS=(
  UserStatus
  AnalysisStatus
  NotificationType
  MessageType
  ReportType
  ReportStatus
  ReportActionType
  TransactionType
  ChatRoomType
  AIRequestType
  AIRequestStatus
  CourseDifficulty
  CourseSpotType
  WalkSpotType
  GoldTransactionType
  GoldTransactionStatus
  PlaceCategory
  NoticeType
)

# Build alternation pattern: UserStatus|AnalysisStatus|...
PATTERN_NAMES=$(IFS='|'; echo "${DOMAIN_ENUMS[*]}")

# Match: `type FOO = '` or `export type FOO = '` — i.e. a literal union starting.
# Excluded: src/api/** (generated), src/types/api.ts (re-export source).
MATCHES=$(git grep -nE "^[[:space:]]*(export[[:space:]]+)?type[[:space:]]+(${PATTERN_NAMES})[[:space:]]*=[[:space:]]*['\"]" \
  -- 'frontend/src' 'admin/src' \
  ':!frontend/src/api' \
  ':!admin/src/api' \
  ':!frontend/src/types/api.ts' \
  ':!admin/src/types/api.ts' \
  || true)

if [ -n "$MATCHES" ]; then
  echo "❌ check-no-literal-enums: hand-written domain enum union detected." >&2
  echo "   These should be re-exported from src/types/api.ts (Schemas[...] derived)." >&2
  echo "" >&2
  echo "$MATCHES" >&2
  exit 1
fi

echo "✓ check-no-literal-enums: no hand-written domain enum unions found."
