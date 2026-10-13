#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
npm --prefix "$project_root/frontend" ci
npm --prefix "$project_root/frontend" run build
bash "$project_root/scripts/mvn.sh" clean verify
printf '\n构建完成：backend/target/cinematch-2.0.0-SNAPSHOT.jar\n运行：java -jar backend/target/cinematch-2.0.0-SNAPSHOT.jar\n'
