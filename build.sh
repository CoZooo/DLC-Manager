#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_VERSION="$(grep -E '^mod_version=' "${ROOT_DIR}/gradle.properties" | cut -d= -f2)"
MODE="${1:-build}"

case "${MODE}" in
  build)
    TASKS=(build)
    ;;
  check)
    TASKS=(check)
    ;;
  archBuild)
    TASKS=(archBuild)
    ;;
  archCheck)
    TASKS=(archCheck)
    ;;
  publish)
    TASKS=(archPublish)
    ;;
  archPublish)
    TASKS=(archPublish)
    ;;
  all)
    TASKS=(check build)
    ;;
  *)
    echo "Usage: ./build.sh [build|check|archBuild|archCheck|publish|archPublish|all]"
    exit 1
    ;;
esac

printf "==> Run tasks: %s\n" "${TASKS[*]}"
"${ROOT_DIR}/gradlew" "${TASKS[@]}" --parallel

printf "==> Done. Output in: %s/output/%s\n" "$ROOT_DIR" "$MOD_VERSION"
