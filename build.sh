#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_VERSION="$(grep -E '^mod_version=' "${ROOT_DIR}/gradle.properties" | cut -d= -f2)"
MODE="${1:-build}"

case "${MODE}" in
  helperBuild)
    TASKS=(helperBuild)
    ;;
  helperCheck)
    TASKS=(helperCheck)
    ;;
  helperClean)
    TASKS=(helperClean)
    ;;
  helperPublish)
    TASKS=(helperPublish)
    ;;
  helperPublishCentral)
    TASKS=(helperPublishCentral)
    ;;
  build)
    TASKS=(build)
    ;;
  test)
    TASKS=(test)
    ;;
  check)
    TASKS=(check)
    ;;
  archBuild)
    TASKS=(build)
    ;;
  archCheck)
    TASKS=(check)
    ;;
  publish)
    TASKS=(publishModPlatforms)
    ;;
  archPublish)
    TASKS=(publishModPlatforms)
    ;;
  all)
    TASKS=(check build)
    ;;
  *)
    echo "Usage: ./build.sh [build|test|check|archBuild|archCheck|publish|archPublish|all|helperBuild|helperCheck|helperClean|helperPublish|helperPublishCentral]"
    exit 1
    ;;
esac

printf "==> Run tasks: %s\n" "${TASKS[*]}"
"${ROOT_DIR}/gradlew" "${TASKS[@]}" --parallel

case "${MODE}" in
  test|check|archCheck|helperCheck)
    printf "==> Verification finished.\n"
    ;;
  *)
    printf "==> Done. Output in: %s/output/%s\n" "$ROOT_DIR" "$MOD_VERSION"
    ;;
esac
