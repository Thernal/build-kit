# Modules, reports, scripts

## Creating modules

```sh
./gradlew create profile api impl wiring        # features/profile/{api,impl,wiring}
./gradlew create core/network api impl          # another area; areas: app.modules.areas
```

Each is a plain multiplatform module (`kmp.library`; `wiring` adds `injection`) with its siblings as
dependencies and empty `commonMain`/`commonTest` source directories; `wiring` gets a Metro
`@BindingContainer @ContributesTo(AppScope::class)` interface to add `@Provides` functions to. Add
`libs.plugins.<alias>.compose` by hand to a module with UI. Existing modules are skipped, never
overwritten. Put code in `domain`, `data` or `presentation` packages (Detekt's layer rules).

The split: `api` holds contracts other modules may see; `impl` implements them and is seen only by
`wiring`; `wiring` binds them into the application graph — an app depends on `wiring`, never on `impl`.

## Dependencies

`implementation(...)` only, in every module that uses a type — the build fails on `api(...)`:

```
:features:profile:impl declares api(...) dependencies: commonMainApi(:core:network:api).
```

Fix: `implementation`, and declare the same dependency in each module that uses its types. The only
exception is a module exported into the iOS framework (`binaries.framework { export(…) }`), which
Kotlin/Native accepts from `api` alone: add its path (or `group:name`) to `app.api.allowed`.

## Reports (never edit `report/` by hand)

| Command | Writes |
|---|---|
| `./gradlew graph` | `report/README.md` + health, build waves, graphs, metrics |
| `./gradlew assembleDevDebug -PcomposeStabilityReport=true`, then `./gradlew composeStabilityReport` | `report/compose-stability/<module>.md` per Compose module + an index |
| `./gradlew :features:x:impl:composeStabilityReport` | that module's page only |
| `./gradlew buildHealth` | `build/reports/dependency-analysis/` — advice, never a failure |

Health verdicts, worst first: **Unused** (nothing depends on it), **Boundary violation** (an `api`
depends on an `impl`/`wiring`), **Concrete hub** (≥3 dependants, mostly concrete classes), **Wide surface**
(≥8 dependencies), **Healthy**. Regenerate `graph` when modules or dependencies change and commit
`report/`. A stability page lists composables that cannot skip and the unstable classes behind them:
make those immutable, `@Immutable`/`@Stable`, or list them in `config/compose/stability.conf`. A run
without metrics keeps the committed pages; the index shows `—` for them.

`report/**/*.md` merges keeping one side; `.githooks/post-merge` regenerates the module report. Commit it.

## Scripts and make

Flavors and apps come from `gradle.properties` everywhere.

```sh
make help
make build-android APP=customer FLAVOR=beta BUILD_TYPE=release   # → .misc/artifacts/android; aab for production release
make verify-android-release APP=customer FLAVOR=prod             # minified, mapping present
make build-ios-framework APP=customer FLAVOR=prod                # :apps:customer:shared
make build-ios APP=customer FLAVOR=beta                          # apps/customer/ios, "Beta Release", scheme customer-Beta-Release
make test test-ios detekt graph
scripts/bump-version-code.sh customer beta                       # before a distributable build
scripts/deeplink.sh 'myapp://profile?id=42' customer dev          # adb, one device; ids as the build computes them
scripts/deeplink.sh --ios 'myapp://profile?id=42'                # booted simulator
```

## Store lanes

`fastlane/Fastfile`: `fastlane android play app:<name> [track:internal]` (production app bundle to Google
Play) and `fastlane ios testflight app:<name> flavor:<flavor>`. Credentials come from the environment only —
`GOOGLE_PLAY_JSON_KEY`, `IOS_BUNDLE_ID_<APP>_<FLAVOR>`, `APP_STORE_CONNECT_API_KEY_PATH`, match's
`MATCH_GIT_URL`/`MATCH_PASSWORD`. Firebase App Distribution is firebase-kit's.
