#!/bin/sh
# Restores one backup made by backup.sh: replaces the whole database and all uploaded photos.
# Stop the backend first, so nothing writes in between (docs/OPERATIONS.md, "Restore a backup"):
#
#   docker compose stop backend backup
#   docker compose run --rm restore 2026-11-18_03-30-00 --yes
#   docker compose up -d
set -eu

name="${1:-}"
if [ -z "$name" ]; then
  echo "Which backup? These are available:" >&2
  found=0
  for backup in /backups/20*/; do
    [ -f "$backup/database.dump" ] || continue
    echo "  $(basename "$backup")" >&2
    found=1
  done
  [ "$found" -eq 1 ] || echo "  (none)" >&2
  exit 1
fi
dir="/backups/$name"
if [ ! -f "$dir/database.dump" ] || [ ! -f "$dir/media.tar.gz" ]; then
  echo "No complete backup at $dir." >&2
  exit 1
fi
if [ "${2:-}" != "--yes" ]; then
  echo "This replaces the shop's database and photos with backup $name." >&2
  echo "Add --yes to go ahead." >&2
  exit 1
fi

export PGPASSWORD="$POSTGRES_PASSWORD"
pg_restore --list "$dir/database.dump" > /dev/null

echo "Restoring the database from $name ..."
psql --host=postgres --username="$POSTGRES_USER" --dbname=postgres -v ON_ERROR_STOP=1 -q <<SQL
DROP DATABASE IF EXISTS "$POSTGRES_DB" WITH (FORCE);
CREATE DATABASE "$POSTGRES_DB" OWNER "$POSTGRES_USER";
SQL
pg_restore --host=postgres --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --exit-on-error --no-owner --role="$POSTGRES_USER" "$dir/database.dump"

echo "Restoring the photos ..."
find /media -mindepth 1 -delete
tar -xzf "$dir/media.tar.gz" -C /media
# The backend runs as user 10001 (backend/Dockerfile): it must own the photos to read and add them.
chown -R "${MEDIA_OWNER:-10001:10001}" /media

echo "Done. Start the backend again; it checks the database schema on start."
