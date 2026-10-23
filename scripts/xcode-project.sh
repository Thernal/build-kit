#!/usr/bin/env bash
# Generates one app's Xcode project from <apps>/<app>/ios/project.yml (xcodegen), with the app's version.
#
# version.properties is the version of both sides of the app. xcodegen expands ${APP_VERSION_NAME} and
# ${APP_VERSION_CODE_<FLAVOR>} in project.yml from the environment, so this script exports them — the same
# values the Android build reads (a flavor on production's id takes production's code) — and project.yml
# turns them into MARKETING_VERSION and CURRENT_PROJECT_VERSION per configuration. A release overrides both
# with xcodebuild arguments (fastlane/Fastfile). An app without ios/project.yml keeps its own project.
#
# Usage: scripts/xcode-project.sh <app>      (make xcode-project APP=<app> runs it)

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/app-properties.sh"

APP="${1:-}"
if [ -z "$APP" ]; then
  echo "Usage: $0 <$(list_apps | paste -sd'|' -)>" >&2
  exit 1
fi
require_app "$APP"

IOS_DIR="$APPS_DIR/$APP/ios"
[ -f "$IOS_DIR/project.yml" ] || exit 0

VERSION_FILE="$APPS_DIR/$APP/version.properties"
version_property() {
  sed -n "s/^$1=//p" "$VERSION_FILE" 2>/dev/null | head -n1 | tr -d '[:space:]'
}

# The defaults are the build's (build-logic/…/application/AppVersion.kt).
APP_VERSION_NAME="$(version_property versionName)"
export APP_VERSION_NAME="${APP_VERSION_NAME:-0.0.1}"
for flavor in $FLAVORS; do
  code="$(version_property "$(version_counter_flavor "$flavor").versionCode")"
  export "APP_VERSION_CODE_$(echo "$flavor" | tr '[:lower:]' '[:upper:]')=${code:-1}"
done

cd "$IOS_DIR" && xcodegen generate --quiet
