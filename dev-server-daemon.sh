#!/bin/bash
while true; do
  if ! curl -s -f -o /dev/null http://localhost:3000/; then
    echo "[$(date)] Dev server not responding, launching Vite on port 3000..." >> /tmp/dev-watchdog.log
    cd /app/applet && npx vite --host 0.0.0.0 --port 3000 >> /tmp/vite.log 2>&1 &
    sleep 3
  fi
  sleep 3
done
