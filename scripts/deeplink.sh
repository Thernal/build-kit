#!/usr/bin/env bash
# Opens a link in an installed app, the way a browser or a notification would — to test deep links
# without sending one.
#
# Usage: scripts/deeplink.sh <url> [app] [flavor]         Android, through adb (one device attached)
#        scripts/deeplink.sh --ios <url>                   the booted iOS simulator, through simctl
#
# On Android the link goes to the <app> build of <flavor> (defaults: the first app, the default
# flavor), so a dev and a production build installed side by side are told apart.

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/app-properties.sh"

usage() {
  echo "Usage: $0 <url> [$(list_apps | paste -sd'|' -)] [${FLAVORS// /|}]"
  echo "       $0 --ios <url>"
}

case "${1:-}" in
  ""|-h|--help) usage; exit 0 ;;
  --ios)
    [ -n "${2:-}" ] || { usage >&2; exit 2; }
    command -v xcrun >/dev/null || { echo "xcrun is not available" >&2; exit 1; }
    exec xcrun simctl openurl booted "$2"
    ;;
esac

url="$1"
app="${2:-$(list_apps | head -n1)}"
flavor="${3:-$DEFAULT_FLAVOR}"
require_app "$app"
require_flavor "$flavor"

command -v adb >/dev/null || { echo "adb is not available" >&2; exit 1; }
devices="$(adb devices | awk 'NR>1 && $2 == "device" { count++ } END { print count+0 }')"
[ "$devices" -eq 1 ] || { echo "expected exactly one authorized device; found $devices" >&2; exit 1; }

package="$(application_id "$app" "$flavor")"
adb shell pm path "$package" >/dev/null 2>&1 || {
  echo "$package is not installed; install the $flavor build of $app first" >&2
  exit 1
}
adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "$url" "$package"
