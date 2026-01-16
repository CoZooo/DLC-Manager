#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

printf "==> Build Forge (1.20.1)\n"
"${ROOT_DIR}/gradlew" :forge:1.20.1:build

printf "==> Build NeoForge (1.21.1)\n"
"${ROOT_DIR}/gradlew" :neoforge:1.21.1:build

printf "==> Build Fabric (1.20.1, 1.21.1)\n"
(
  cd "${ROOT_DIR}/fabric-build"
  ./gradlew :fabric:1.20.1:remapJar
  ./gradlew :fabric:1.21.1:remapJar
)

printf "==> Done. Output in: %s/output/%s\n" "$ROOT_DIR" "$(grep -E '^mod_version=' "$ROOT_DIR/gradle.properties" | cut -d= -f2)"
