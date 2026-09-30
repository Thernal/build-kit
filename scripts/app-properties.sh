#!/usr/bin/env bash
# Shared by the scripts in this directory: reads build-kit's `app.*` keys from gradle.properties, so
# flavors, apps and ids are declared once — where the build reads them.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

app_property() {
  sed -n "s/^$1=//p" "$REPO_ROOT/gradle.properties" | tail -n1 | tr -d '[:space:]'
}

FLAVORS="$(app_property app.flavors | tr ',' ' ')"
PRODUCTION_FLAVOR="$(app_property app.flavors.production)"
[ -n "$PRODUCTION_FLAVOR" ] || PRODUCTION_FLAVOR="${FLAVORS##* }"
DEFAULT_FLAVOR="$(app_property app.flavors.default)"
[ -n "$DEFAULT_FLAVOR" ] || DEFAULT_FLAVOR="${FLAVORS%% *}"
NAMESPACE="$(app_property app.namespace)"
MODULES_ROOT="$(app_property app.modules.root)"
APPS_DIR="$REPO_ROOT/${MODULES_ROOT:+$MODULES_ROOT/}apps"

# Application modules: every directory under apps/ with a build file.
list_apps() {
  for dir in "$APPS_DIR"/*/; do
    [ -f "$dir/build.gradle.kts" ] && basename "$dir"
  done
}

require_flavor() {
  case " $FLAVORS " in
    *" $1 "*) ;;
    *) echo "unknown flavor '$1'; expected one of: $FLAVORS" >&2; exit 2 ;;
  esac
}

require_app() {
  [ -f "$APPS_DIR/$1/build.gradle.kts" ] || {
    echo "unknown app '$1'; expected one of: $(list_apps | tr '\n' ' ')" >&2
    exit 2
  }
}

# The installed package of <app> in <flavor>: the module's own `applicationId = "…"` when it sets
# one, else the convention's default (namespace + module path), plus `.<flavor>` off production.
application_id() {
  local app="$1" flavor="$2" id
  id="$(sed -n 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"\(.*\)".*/\1/p' "$APPS_DIR/$app/build.gradle.kts" | head -n1)"
  if [ -z "$id" ]; then
    id="$NAMESPACE.${MODULES_ROOT:+$(echo "$MODULES_ROOT" | tr '/' '.').}apps.$(echo "$app" | tr -d '-')"
  fi
  [ "$flavor" = "$PRODUCTION_FLAVOR" ] && echo "$id" || echo "$id.$flavor"
}
