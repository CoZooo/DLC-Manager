#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLEW="${ROOT_DIR}/../../gradlew"
MODE="${1:-build}"
TASK=""

case "${MODE}" in
  build)
    TASK="helperBuild"
    ;;
  check)
    TASK="helperCheck"
    ;;
  clean)
    TASK="helperClean"
    ;;
  publish)
    TASK="publish"
    ;;
  *)
    echo "Usage: ./tools/file-ops-helper/build.sh [build|check|clean|publish]"
    exit 1
    ;;
esac

printf "==> Helper task: %s\n" "${TASK}"
"${GRADLEW}" --project-dir "${ROOT_DIR}" "${TASK}" --parallel

printf "==> Helper %s finished.\n" "${MODE}"
