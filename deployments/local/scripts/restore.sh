#!/usr/bin/env bash
#
# restore.sh — MongoDB restore for the local e-commerce stack.
#
# Usage:
#   ./scripts/restore.sh <backup_file>
#
# The backup file is a per-context archive produced by backup.sh
# (mongo_<context>_<timestamp>.archive.gz). Restoring drops and recreates the
# target database's collections, so this is destructive — a confirmation prompt
# is shown unless --yes is passed.
#
#   ./scripts/restore.sh --yes backups/mongo_sales_20260101_000000.archive.gz
#
# Procedure (Fase 6 checklist 11.3):
#   1. Validate the backup file exists.
#   2. Confirm intent (destructive).
#   3. Drop + restore the target database from the archive.
#   4. Report success/failure.

set -euo pipefail

COMPOSE_FILE="$(cd "$(dirname "$0")/.." && pwd)/docker-compose.yml"

AUTO_YES=0
BACKUP_FILE=""

for arg in "$@"; do
  case "$arg" in
    --yes) AUTO_YES=1 ;;
    *)     BACKUP_FILE="$arg" ;;
  esac
done

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 [--yes] <backup_file>" >&2
  echo "  backup_file: path to a mongo_<context>_<timestamp>.archive.gz dump" >&2
  exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
  echo "ERROR: backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

# Validate the dump BEFORE the destructive restore: a truncated/empty/corrupt
# file would wipe the database and restore nothing.
if [ ! -s "$BACKUP_FILE" ] || [ "$(stat -c%s "$BACKUP_FILE")" -lt 1024 ]; then
  echo "ERROR: backup file is empty or too small (< 1 KB); refusing to restore." >&2
  exit 1
fi

if [[ "$BACKUP_FILE" == *.gz ]]; then
  if ! gzip -t "$BACKUP_FILE" 2>/dev/null; then
    echo "ERROR: backup file is not a valid gzip archive; refusing to restore." >&2
    exit 1
  fi
fi

# Derive the context (and therefore the compose service) from the file name:
# mongo_<context>_<timestamp>.archive.gz -> mongo_<context>
BASENAME=$(basename "$BACKUP_FILE")
if [[ ! "$BASENAME" =~ ^(mongo_[a-z]+)_[0-9]{8}_ ]]; then
  echo "ERROR: cannot derive context from '${BASENAME}'." >&2
  echo "       Expected a file named mongo_<context>_<timestamp>.archive.gz." >&2
  exit 1
fi
SERVICE="${BASH_REMATCH[1]}"
DB_NAME="ec_${SERVICE#mongo_}"

MONGO_ID=$(docker compose -f "${COMPOSE_FILE}" ps -q "${SERVICE}" 2>/dev/null || true)
if [ -z "$MONGO_ID" ]; then
  echo "ERROR: ${SERVICE} container is not present/running in compose." >&2
  exit 1
fi

if [ "$AUTO_YES" -ne 1 ]; then
  read -r -p "Restore will DROP and recreate database '${DB_NAME}' on ${SERVICE}. Continue? [y/N] " answer
  case "$answer" in
    y|Y) ;;
    *) echo "Aborted."; exit 1 ;;
  esac
fi

echo "Restoring ${BACKUP_FILE} -> ${DB_NAME} (${SERVICE})"

# --drop replaces each collection present in the archive; the archive already
# contains the full database snapshot taken by mongodump.
docker compose -f "${COMPOSE_FILE}" exec -T "${SERVICE}" \
  mongorestore --archive --gzip --drop < "$BACKUP_FILE"

echo "Restore complete: ${DB_NAME} is back in service."
