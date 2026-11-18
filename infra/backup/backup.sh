#!/bin/sh
# One backup in /backups/<yyyy-mm-dd>_<hh-mm-ss>/: database.dump (pg_dump, custom format) and
# media.tar.gz (uploaded product and gallery photos). Backups older than BACKUP_KEEP_DAYS days
# are deleted. If BACKUP_PING_URL is set (e.g. a healthchecks.io check), it is called on
# success and "<url>/fail" on failure, so a missed or failed backup raises an alert.
#
# Run one by hand: docker compose exec backup sh /opt/backup/backup.sh
set -eu

ping() {
  if [ -n "${BACKUP_PING_URL:-}" ]; then
    wget -q -T 10 -O /dev/null "$1" || echo "Could not reach the backup monitor." >&2
  fi
}

name=$(date +%Y-%m-%d_%H-%M-%S)
work="/backups/.incomplete-$name"
on_exit() {
  status=$?
  if [ "$status" -ne 0 ]; then
    rm -rf "$work"
    ping "${BACKUP_PING_URL:-}/fail"
  fi
}
trap on_exit EXIT

mkdir -p "$work"
export PGPASSWORD="$POSTGRES_PASSWORD"
pg_dump --host=postgres --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --format=custom --compress=6 --file="$work/database.dump"
# Reading the table of contents fails if the dump is damaged.
pg_restore --list "$work/database.dump" > /dev/null
tar -czf "$work/media.tar.gz" -C /media .
if [ -e "/backups/$name" ]; then
  echo "A backup named $name exists already." >&2
  exit 1
fi
mv "$work" "/backups/$name"

# Keep BACKUP_KEEP_DAYS days; never delete the backup just made.
find /backups -mindepth 1 -maxdepth 1 -type d -name '20*' ! -name "$name" \
  -mtime +"${BACKUP_KEEP_DAYS:-14}" -exec rm -rf {} +

echo "Backup $name: database $(du -h "/backups/$name/database.dump" | cut -f1), photos $(du -h "/backups/$name/media.tar.gz" | cut -f1)."
ping "${BACKUP_PING_URL:-}"
