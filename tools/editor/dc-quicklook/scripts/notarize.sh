#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

if [[ $# -gt 1 ]]; then
  echo "Usage: $0 [submission-id]" >&2
  exit 2
fi

AUTH=()
if [[ -n "${NOTARY_KEYCHAIN_PROFILE:-}" ]]; then
  AUTH=(--keychain-profile "$NOTARY_KEYCHAIN_PROFILE")
elif [[ -n "${NOTARY_KEY_FILE:-}" && -n "${NOTARY_KEY_ID:-}" ]]; then
  AUTH=(--key "$NOTARY_KEY_FILE" --key-id "$NOTARY_KEY_ID")
  if [[ -n "${NOTARY_ISSUER:-}" ]]; then
    AUTH+=(--issuer "$NOTARY_ISSUER")
  fi
else
  echo "Set NOTARY_KEYCHAIN_PROFILE, or NOTARY_KEY_FILE and NOTARY_KEY_ID (plus NOTARY_ISSUER for a team key)." >&2
  exit 2
fi

APP="${NOTARY_APP:-build/DerivedData/Build/Products/Release/DCQuickLookHost.app}"
if [[ ! -d "$APP" ]]; then
  echo "Signed app not found: $APP. Run ./scripts/build_signed.sh first." >&2
  exit 1
fi
codesign --verify --deep --strict --verbose=2 "$APP"
VERSION="$(/usr/libexec/PlistBuddy -c 'Print CFBundleShortVersionString' "$APP/Contents/Info.plist")"
mkdir -p build/notarization build/distributions
WORK="$(mktemp -d build/notarization/.work.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
SUBMISSION_ID="${1:-${NOTARY_SUBMISSION_ID:-}}"

json_field() {
  python3 - "$1" "$2" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as source:
    value = json.load(source).get(sys.argv[2])
if not isinstance(value, str) or not value:
    raise SystemExit('Missing field in notary response: ' + sys.argv[2])
print(value)
PY
}

if [[ -z "$SUBMISSION_ID" ]]; then
  ARCHIVE="${NOTARY_ARCHIVE:-$WORK/submitted.zip}"
  if [[ -z "${NOTARY_ARCHIVE:-}" ]]; then
    ditto -c -k --sequesterRsrc --keepParent "$APP" "$ARCHIVE"
  fi
  if [[ ! -f "$ARCHIVE" ]]; then
    echo "Archive not found: $ARCHIVE" >&2
    exit 1
  fi
  xcrun notarytool submit "$ARCHIVE" "${AUTH[@]}" --no-wait --no-progress --output-format json > "$WORK/submission.json"
  SUBMISSION_ID="$(json_field "$WORK/submission.json" id)"
fi
if [[ ! "$SUBMISSION_ID" =~ ^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$ ]]; then
  echo "Invalid submission ID." >&2
  exit 2
fi
REPORT_DIR="build/notarization/$SUBMISSION_ID"
mkdir -p "$REPORT_DIR"
if [[ -f "$WORK/submission.json" ]]; then
  mv "$WORK/submission.json" "$REPORT_DIR/submission.json"
  shasum -a 256 "$ARCHIVE" | awk '{print $1}' > "$REPORT_DIR/submitted.sha256"
fi
printf '%s\n' "$SUBMISSION_ID" > build/notarization/latest-submission-id.txt
echo "Submission: $SUBMISSION_ID"
echo "Reports: $REPORT_DIR"

while true; do
  xcrun notarytool info "$SUBMISSION_ID" "${AUTH[@]}" --no-progress --output-format json > "$WORK/status.json"
  mv "$WORK/status.json" "$REPORT_DIR/status.json"
  STATUS="$(json_field "$REPORT_DIR/status.json" status)"
  case "$STATUS" in
    Accepted) break ;;
    'In Progress')
      echo "Apple is processing $SUBMISSION_ID; waiting up to 60 seconds."
      xcrun notarytool wait "$SUBMISSION_ID" "${AUTH[@]}" --timeout 60s --no-progress --output-format json > "$REPORT_DIR/wait.json" 2> "$REPORT_DIR/wait.stderr" || true
      ;;
    *)
      xcrun notarytool log "$SUBMISSION_ID" "${AUTH[@]}" "$REPORT_DIR/notary-log.json" > "$REPORT_DIR/log.stdout" 2> "$REPORT_DIR/log.stderr" || true
      echo "Notarization ended with status '$STATUS'. See $REPORT_DIR; no distribution was created." >&2
      exit 1
      ;;
  esac
done

xcrun notarytool log "$SUBMISSION_ID" "${AUTH[@]}" "$REPORT_DIR/notary-log.json" > "$REPORT_DIR/log.stdout"
xcrun stapler staple "$APP" > "$REPORT_DIR/staple.log" 2>&1
xcrun stapler validate "$APP" > "$REPORT_DIR/validate.log" 2>&1
codesign --verify --deep --strict --verbose=2 "$APP" > "$REPORT_DIR/codesign.log" 2>&1
ZIP="build/distributions/DCQuickLookHost-$VERSION-macos-notarized.zip"
ditto -c -k --sequesterRsrc --keepParent "$APP" "$WORK/notarized.zip"
mv "$WORK/notarized.zip" "$ZIP"
shasum -a 256 "$ZIP" > "$ZIP.sha256"
python3 - "$REPORT_DIR/report.json" "$SUBMISSION_ID" "$APP" "$ZIP" <<'PY'
import hashlib, json, os, sys
report, submission, app, archive = sys.argv[1:]
with open(archive, 'rb') as source:
    digest = hashlib.file_digest(source, 'sha256').hexdigest() if hasattr(hashlib, 'file_digest') else hashlib.sha256(source.read()).hexdigest()
with open(report, 'w', encoding='utf-8') as target:
    json.dump({'submissionId': submission, 'status': 'Accepted', 'stapled': True,
               'app': os.path.relpath(app), 'archive': os.path.relpath(archive), 'sha256': digest}, target, indent=2)
    target.write('\n')
PY
echo "Notarized and stapled: $ZIP"
echo "Verification report: $REPORT_DIR/report.json"
