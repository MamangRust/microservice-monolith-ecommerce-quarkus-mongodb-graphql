#!/usr/bin/env bash
#
# backup.sh — MongoDB backup for the local e-commerce stack.
#
# Usage:
#   ./scripts/backup.sh [retention_days]
#
# Produces one archive per bounded context:
#   deployments/local/backups/<context>_<timestamp>.archive.gz
# Prunes archives older than RETENTION_DAYS (default 7).
#
# Procedure (Fase 6 checklist 11.3):
#   1. Verify each mongo_* container is running.
#   2. mongodump each context database into a gzip archive (portable).
#   3. Keep archives in ./backups/.
#   4. Prune old archives so retention is bounded.
#
# There is one MongoDB instance per bounded context (no proxy), so each context
# is dumped independently and can be restored independently.

set -euo pipefail

COMPOSE_FILE="$(cd "$(dirname "$0")/.." && pwd)/docker-compose.yml"
BACKUP_DIR="$(cd "$(dirname "$0")/.." && pwd)/backups"
RETENTION_DAYS="${1:-7}"

# "<compose_service>:<database>" — one entry per bounded context.
CONTEXTS=(
  "mongo_identity:ec_identity"
  "mongo_merchant:ec_merchant"
  "mongo_catalog:ec_catalog"
  "mongo_sales:ec_sales"
  "mongo_experience:ec_experience"
  "mongo_email:ec_email"
)

mkdir -p "${BACKUP_DIR}"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)

for entry in "${CONTEXTS[@]}"; do
  SERVICE="${entry%%:*}"
  DB_NAME="${entry##*:}"

  # `compose ps` exits 0 even when the service is absent/stopped, so check the
  # actual container state via inspect instead of relying on its exit code.
  MONGO_ID=$(docker compose -f "${COMPOSE_FILE}" ps -q "${SERVICE}" 2>/dev/null || true)
  if [ -z "$MONGO_ID" ]; then
    echo "ERROR: ${SERVICE} container is not running. Start the stack first (just up)." >&2
    exit 1
  fi
  MONGO_STATE=$(docker inspect -f '{{.State.Running}}' "$MONGO_ID" 2>/dev/null || echo false)
  if [ "$MONGO_STATE" != "true" ]; then
    echo "ERROR: ${SERVICE} container is not running. Start the stack first (just up)." >&2
    exit 1
  fi

  OUT_FILE="${BACKUP_DIR}/${SERVICE}_${TIMESTAMP}.archive.gz"
  echo "Backing up ${DB_NAME} (${SERVICE}) -> ${OUT_FILE}"

  docker compose -f "${COMPOSE_FILE}" exec -T "${SERVICE}" \
    mongodump --db "${DB_NAME}" --archive --gzip > "${OUT_FILE}"

  # Exit early if the archive is empty or suspiciously small (< 1 KB).
  if [ ! -s "${OUT_FILE}" ] || [ "$(stat -c%s "${OUT_FILE}")" -lt 1024 ]; then
    echo "ERROR: backup file ${OUT_FILE} is empty or too small; removing." >&2
    rm -f "${OUT_FILE}"
    exit 1
  fi

  echo "Backup complete: ${OUT_FILE} ($(du -h "${OUT_FILE}" | cut -f1))"
done

# Prune old backups beyond the retention window.
PRUNE_TS=$(date -d "-${RETENTION_DAYS} days" +%Y%m%d 2>/dev/null || date -v-${RETENTION_DAYS}d +%Y%m%d)
PRUNED=0
for f in "${BACKUP_DIR}"/mongo_*.archive.gz; do
  [ -e "$f" ] || continue
  fdate=$(basename "$f" | sed -E 's/mongo_[a-z]+_([0-9]{8})_.*/\1/')
  if [ -n "$fdate" ] && [ "$fdate" -lt "$PRUNE_TS" ]; then
    rm -f "$f"
    PRUNED=$((PRUNED + 1))
  fi
done

echo "Pruned ${PRUNED} backup(s) older than ${RETENTION_DAYS} day(s)."
echo "Done."
