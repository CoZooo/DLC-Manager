#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_VERSION="$(grep -E '^mod_version=' "${ROOT_DIR}/gradle.properties" | cut -d= -f2)"

printf "==> Build Architectury 1.20.1 (Fabric + Forge)\n"
"${ROOT_DIR}/gradlew" -p "${ROOT_DIR}/architectury/1.20.1" :fabric:remapJar :forge:remapJar --parallel

printf "==> Build Architectury 1.21.1 (Fabric + NeoForge)\n"
"${ROOT_DIR}/gradlew" -p "${ROOT_DIR}/architectury/1.21.1" :fabric:remapJar :neoforge:remapJar --parallel

printf "==> Done. Output in: %s/output/%s\n" "$ROOT_DIR" "$MOD_VERSION"
