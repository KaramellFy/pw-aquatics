#!/bin/sh
# Runs backup.sh every day at BACKUP_TIME (HH:MM, time zone TZ, default 03:30 Europe/Berlin).
# A failed backup is reported to BACKUP_PING_URL and in the log; the next one runs as planned.
set -u
time="${BACKUP_TIME:-03:30}"
case "$time" in
  [0-2][0-9]:[0-5][0-9]) ;;
  *) echo "BACKUP_TIME must look like 03:30, not '$time'" >&2; exit 1 ;;
esac
echo "Backups run daily at $time ($(date +%Z)), kept for ${BACKUP_KEEP_DAYS:-14} days."
while true; do
  now=$(date +%s)
  next=$(date -d "$(date +%Y-%m-%d) $time" +%s)
  if [ "$next" -le "$now" ]; then
    next=$((next + 86400))
  fi
  sleep $((next - now))
  sh /opt/backup/backup.sh || echo "Backup failed; see the lines above." >&2
done
