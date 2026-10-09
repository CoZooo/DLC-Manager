#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"
TEAM_ID="${DEVELOPMENT_TEAM:?Set DEVELOPMENT_TEAM to your Apple Developer Team ID.}"
IDENTITY="${SIGNING_IDENTITY:-}"
if [[ -z "$IDENTITY" ]]; then
  IDENTITY="$(/usr/bin/security find-identity -v -p codesigning | /usr/bin/awk -v team="$TEAM_ID" '/Developer ID Application:/ && index($0, "(" team ")") { print $2; exit }')"
fi
if [[ -z "$IDENTITY" ]]; then
  echo "No Developer ID Application identity for team $TEAM_ID. Set SIGNING_IDENTITY and DEVELOPMENT_TEAM." >&2
  exit 1
fi
./scripts/generate_project.sh
mkdir -p build
/usr/bin/xcodebuild \
  -project DCQuickLook.xcodeproj \
  -scheme DCQuickLookHost \
  -configuration Release \
  -destination 'generic/platform=macOS' \
  -derivedDataPath build/DerivedData \
  ARCHS='arm64 x86_64' \
  ONLY_ACTIVE_ARCH=NO \
  CODE_SIGN_STYLE=Manual \
  "CODE_SIGN_IDENTITY=$IDENTITY" \
  "DEVELOPMENT_TEAM=$TEAM_ID" \
  CODE_SIGN_INJECT_BASE_ENTITLEMENTS=NO \
  ENABLE_HARDENED_RUNTIME=YES \
  REGISTER_APP_WITH_LAUNCH_SERVICES=NO \
  OTHER_CODE_SIGN_FLAGS=--timestamp \
  build > build/signed-build.log 2>&1 || {
    tail -80 build/signed-build.log >&2
    exit 1
  }
APP=build/DerivedData/Build/Products/Release/DCQuickLookHost.app
EXTENSION="$APP/Contents/PlugIns/DCQuickLookExtension.appex"
for BUNDLE in "$EXTENSION" "$APP"; do
  /usr/bin/codesign --verify --strict --verbose=2 "$BUNDLE"
  DETAILS="$(/usr/bin/codesign -dvv "$BUNDLE" 2>&1)"
  if ! /usr/bin/grep -Fq "TeamIdentifier=$TEAM_ID" <<< "$DETAILS"; then
    echo "Unexpected signing team: $BUNDLE" >&2
    exit 1
  fi
  if ! /usr/bin/grep -Fq 'Authority=Developer ID Application:' <<< "$DETAILS"; then
    echo "Bundle was not signed using Developer ID Application: $BUNDLE" >&2
    exit 1
  fi
  if ! /usr/bin/grep -q 'flags=.*runtime' <<< "$DETAILS"; then
    echo "Hardened Runtime is missing: $BUNDLE" >&2
    exit 1
  fi
  ENTITLEMENTS="$(mktemp -t dc-quicklook-entitlements)"
  /usr/bin/codesign -d --entitlements - --xml "$BUNDLE" > "$ENTITLEMENTS" 2>/dev/null
  /usr/bin/python3 - "$ENTITLEMENTS" <<'PY'
import plistlib, sys
with open(sys.argv[1], 'rb') as source:
    entitlements = plistlib.load(source)
assert entitlements.get('com.apple.security.app-sandbox') is True
assert not entitlements.get('com.apple.security.get-task-allow', False)
PY
  /bin/rm -f "$ENTITLEMENTS"
done
/usr/bin/codesign --verify --deep --strict --verbose=2 "$APP"
VERSION="$(/usr/libexec/PlistBuddy -c 'Print CFBundleShortVersionString' "$APP/Contents/Info.plist")"
mkdir -p build/distributions
ZIP="build/distributions/DCQuickLookHost-$VERSION-macos.zip"
/usr/bin/ditto -c -k --sequesterRsrc --keepParent "$APP" "$ZIP"
/usr/bin/shasum -a 256 "$ZIP" > "$ZIP.sha256"
echo "Signed for team $TEAM_ID: $APP"
echo "Distribution: $ZIP"
echo "Build log: build/signed-build.log"
