#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen not found."
  echo "Install: brew install xcodegen"
  exit 1
fi

cd "$ROOT_DIR"
python3 scripts/sync_language.py
xcodegen generate
echo "Generated: ${ROOT_DIR}/DCQuickLook.xcodeproj"
