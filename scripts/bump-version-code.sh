#!/usr/bin/env bash
# Bumps one app's versionCode for one flavor in <apps>/<app>/version.properties by 1.
#
# Each flavor is a separate application to the stores, so each keeps its own counter and climbs at the
# pace it actually ships. versionName is not touched; change it by hand in the same file.
# Run it right before building something distributable, so the artifact carries the new number.
#
# Usage: scripts/bump-version-code.sh <app> <flavor>

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/app-properties.sh"

APP="${1:-}"
FLAVOR="${2:-}"
if [ -z "$APP" ] || [ -z "$FLAVOR" ]; then
  echo "Usage: $0 <$(list_apps | paste -sd'|' -)> <${FLAVORS// /|}>" >&2
  exit 1
fi
require_app "$APP"
require_flavor "$FLAVOR"

VERSION_FILE="$APPS_DIR/$APP/version.properties"
KEY="$FLAVOR.versionCode"
CURRENT="$(sed -n "s/^$KEY=//p" "$VERSION_FILE" 2>/dev/null | head -n1)"
[ -n "$CURRENT" ] || CURRENT=1   # the build's default when the key is missing

NEXT=$((CURRENT + 1))
if grep -q "^$KEY=" "$VERSION_FILE" 2>/dev/null; then
  sed -i.bak -E "s/^$KEY=.*/$KEY=$NEXT/" "$VERSION_FILE" && rm -f "$VERSION_FILE.bak"
else
  echo "$KEY=$NEXT" >>"$VERSION_FILE"
fi
echo "Bumped $APP $KEY: $CURRENT -> $NEXT"
