#!/bin/bash
# Runs ON THE SERVER. Swaps in /root/resona/next.jar, restarts the app and
# rolls back to the previous jar if the app is not answering within 10 minutes.
#
# Usage: deploy.sh <expected-sha256-of-next.jar>
set -euo pipefail

APP_DIR=/root/resona
JAR=$APP_DIR/media-management-0.0.1-SNAPSHOT.jar
NEXT=$APP_DIR/next.jar
BACKUPS=/root/backups
HEALTH_URL=http://127.0.0.1:8080/auth/login
TS=$(date +%Y%m%d-%H%M%S)

expected_sha=${1:?expected sha256 of next.jar is required}
actual_sha=$(sha256sum "$NEXT" | cut -d' ' -f1)
if [ "$actual_sha" != "$expected_sha" ]; then
  echo "Checksum mismatch: expected $expected_sha, got $actual_sha. Nothing changed."
  exit 1
fi

mkdir -p "$BACKUPS"
(cd /tmp && su postgres -c "pg_dump -p 5433 -Fc postgres") > "$BACKUPS/db-$TS.dump"
echo "Database backup: $BACKUPS/db-$TS.dump ($(pg_restore -l "$BACKUPS/db-$TS.dump" | grep -c 'TABLE DATA') tables)"
cp -p "$JAR" "$BACKUPS/app-before-deploy-$TS.jar"

wait_healthy() {
  for _ in $(seq 1 20); do
    if [ "$(curl -sL -m 20 -o /dev/null -w '%{http_code}' "$HEALTH_URL")" = "200" ]; then return 0; fi
    systemctl -q is-active resona || return 1
    sleep 30
  done
  return 1
}

mv "$NEXT" "$JAR"
systemctl restart resona
echo "Restarted at $(date -u +%H:%M:%S) UTC, waiting for the app..."

if wait_healthy; then
  echo "Deploy OK: app is answering."
  ls -t "$BACKUPS"/app-before-deploy-*.jar 2>/dev/null | tail -n +6 | xargs -r rm -f
  exit 0
fi

echo "App did not come up. Rolling back to the previous jar."
cp -p "$BACKUPS/app-before-deploy-$TS.jar" "$JAR"
systemctl restart resona
if wait_healthy; then
  echo "Rolled back: the previous version is running again."
else
  echo "ROLLBACK ALSO FAILED: check the server now."
fi
exit 1
