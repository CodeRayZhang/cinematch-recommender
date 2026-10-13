#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
for candidate in "$project_root"/.tools/jdk-*/Contents/Home; do
  if [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
done
maven_bin="$(command -v mvn || true)"
if [ -z "$maven_bin" ]; then
  for candidate in "$project_root"/.tools/apache-maven-*/bin/mvn; do
    if [ -x "$candidate" ]; then maven_bin="$candidate"; break; fi
  done
fi
if [ -z "$maven_bin" ]; then echo '请安装 Java 21 和 Maven 3.9+' >&2; exit 1; fi
args=(-f "$project_root/backend/pom.xml" "-Dmaven.repo.local=$project_root/.cache/m2")
if [ -f "$project_root/.cache/maven-settings.xml" ]; then args+=(-s "$project_root/.cache/maven-settings.xml"); fi
cd "$project_root/backend"
exec "$maven_bin" "${args[@]}" "$@"
