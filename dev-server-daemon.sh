#!/bin/bash
while true; do
  if ! curl -s -f -o /dev/null http://localhost:3000/api/v1/health; then
    echo "[$(date)] Dev server not responding, launching fullstack server on port 3000..." >> /tmp/dev-watchdog.log
    # Kill any stale node or vite processes on 3000 if hung
    fuser -k 3000/tcp 2>/dev/null || true
    sleep 1
    cd /app/applet && npx tsx server.ts >> /tmp/server.log 2>&1 &
    sleep 4
  fi
  sleep 4
done
