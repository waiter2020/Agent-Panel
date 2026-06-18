#!/bin/sh
set -e

nginx -g 'daemon on;'

LOG_PATH="${LOG_PATH:-/var/log/agent-panel}"
mkdir -p "$LOG_PATH"

echo "[entrypoint] LOG_PATH=$LOG_PATH SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}"
echo "Starting Agent Panel backend..."
exec java ${JAVA_OPTS} -jar /app/app.jar