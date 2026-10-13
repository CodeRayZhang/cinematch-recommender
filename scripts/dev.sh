#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
if [ ! -d "$project_root/frontend/node_modules" ]; then npm --prefix "$project_root/frontend" ci; fi
backend_pid=''
frontend_pid=''
cleanup() {
  trap - EXIT INT TERM
  [ -z "$frontend_pid" ] || kill "$frontend_pid" 2>/dev/null || true
  [ -z "$backend_pid" ] || kill "$backend_pid" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM
bash "$project_root/scripts/mvn.sh" spring-boot:run &
backend_pid=$!
(cd "$project_root/frontend" && exec node node_modules/vite/bin/vite.js --host 127.0.0.1) &
frontend_pid=$!
printf '\nCineMatch开发环境：http://127.0.0.1:5173\n按 Ctrl+C 停止。\n'
while kill -0 "$backend_pid" 2>/dev/null && kill -0 "$frontend_pid" 2>/dev/null; do sleep 1; done
exit 1
