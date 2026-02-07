#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

printf "==> Build Forge + NeoForge\n"
"${ROOT_DIR}/gradlew" build --parallel

#printf "==> Build Fabric\n"
#(
#  cd "${ROOT_DIR}/fabric-build"
#  ./gradlew build --parallel
#)

printf "==> Done. Output in: %s/output/%s\n" "$ROOT_DIR" "$(grep -E '^mod_version=' "$ROOT_DIR/gradle.properties" | cut -d= -f2)"
