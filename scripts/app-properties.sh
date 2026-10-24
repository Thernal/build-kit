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

# Apps: every apps/<name>/ with an Android side (android/build.gradle.kts); each also has ios/ and shared/.
list_apps() {
  for dir in "$APPS_DIR"/*/; do
    [ -f "$dir/android/build.gradle.kts" ] && basename "$dir"
  done
}

require_flavor() {
  case " $FLAVORS " in
    *" $1 "*) ;;
    *) echo "unknown flavor '$1'; expected one of: $FLAVORS" >&2; exit 2 ;;
  esac
}

require_app() {
  [ -f "$APPS_DIR/$1/android/build.gradle.kts" ] || {
    echo "unknown app '$1'; expected one of: $(list_apps | tr '\n' ' ')" >&2
    exit 2
  }
}

# The application id suffix of <flavor>, without the dot, as the build gives it: none for production and
# for `app.flavors.<flavor>.idSuffix=none`, the override when one is set, else the flavor's name.
id_suffix() {
  local flavor="$1" override
  [ "$flavor" = "$PRODUCTION_FLAVOR" ] && return 0
  override="$(app_property "app.flavors.$flavor.idSuffix")"
  override="${override#.}"
  case "$override" in
    none) ;;
    '') echo "$flavor" ;;
    *) echo "$override" ;;
  esac
}

# The flavor whose versionCode <flavor> uses: production's for a flavor on production's id (the same store
# application), its own otherwise.
version_counter_flavor() {
  if [ -n "$(id_suffix "$1")" ]; then echo "$1"; else echo "$PRODUCTION_FLAVOR"; fi
}

# The installed package of <app> in <flavor>: its Android module's own `applicationId = "…"` when it
# sets one, else the convention's default (namespace + module path below the modules root, `-` read as `.`),
# plus the flavor's id suffix.
application_id() {
  local app="$1" flavor="$2" id suffix
  id="$(sed -n 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"\(.*\)".*/\1/p' "$APPS_DIR/$app/android/build.gradle.kts" | head -n1)"
  if [ -z "$id" ]; then
    id="$NAMESPACE.apps.$(echo "$app" | tr '-' '.' | tr -cd 'A-Za-z0-9_.' | tr -s '.').android"
  fi
  suffix="$(id_suffix "$flavor")"
  echo "$id${suffix:+.$suffix}"
}
