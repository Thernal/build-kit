# Modules, reports, scripts

## Creating modules

```sh
./gradlew create profile api impl wiring        # features/profile/{api,impl,wiring}
./gradlew create core/network api impl          # another area; areas: app.modules.areas
./gradlew create core/common                    # no kind: one module at the path
```

Each is a plain multiplatform module (`kmp.library`; `wiring` adds `injection`) with its siblings as
dependencies and empty `commonMain`/`commonTest` source directories; `wiring` gets a Metro
`@BindingContainer @ContributesTo(AppScope::class)` interface, `<Name>ProvidersModule`, to add `@Provides`
functions to — a `wiring` module's containers are named for what they bind (`<Name>ViewModelsModule`,
`<Name>ProvidersModule`, `<Name>BindingsModule`). Add
`libs.plugins.<alias>.compose` by hand to a module with UI. Existing modules are skipped, never
overwritten. Put code in `domain`, `data` or `presentation` packages (Detekt's layer rules).

The split: `api` holds contracts other modules may see; `impl` implements them and is seen only by
`wiring`; `wiring` binds them into the application graph — an app depends on `wiring`, never on `impl`.

## The app's graph

The module that declares the Metro graph (`apps/<name>/shared`) applies `libs.plugins.<alias>.graph`
instead of `injection`. It adds every `…:wiring` module outside `apps` — a module `create` makes joins the
graph with no edit to the app — so the app's build file lists only what its own code imports (the
`api` modules of the routes and contracts it opens; never an `impl`):

```kotlin
plugins {
    alias(libs.plugins.<alias>.compose)
    alias(libs.plugins.<alias>.graph)
}
```

A wiring module that must stay out goes in `gradle.properties`, as a module path or a directory of them:
`app.graph.exclude=:core:firebase` for every app (a capability not set up yet), `app.graph.client.exclude=
:features:store` for one (another app's features). A path that names no module fails the build.

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

`report/**/*.md` merges keeping one side; `.githooks/post-merge` regenerates the module report and commits
it on top of the merge (`chore(report): regenerate after merging <ref>`) — push that commit with the merge.
After `git merge --squash` it is only staged and goes into the squash commit.

## Scripts and make

Flavors and apps come from `gradle.properties` everywhere.

```sh
make help
make build-android APP=customer FLAVOR=beta BUILD_TYPE=release   # → .misc/artifacts/android; aab for production release
make verify-android-release APP=customer FLAVOR=prod             # minified, mapping present
make build-ios-framework APP=customer FLAVOR=prod                # :apps:customer:shared
make build-ios APP=customer FLAVOR=beta                          # apps/customer/ios, "Beta Release", scheme customer-Beta-Release
make xcode-project APP=customer                                  # project.yml → .xcodeproj with version.properties' version
make test test-ios test-fastlane detekt graph                    # test-fastlane: the store lanes' Ruby helpers
scripts/bump-version-code.sh customer beta                       # before a distributable build made by hand; an idSuffix=none flavor bumps production's code
make distribute-android APP=customer FLAVOR=beta TESTER_GROUPS=qa # to Firebase App Distribution (firebase-kit's script), versionCode +1 first, committed once sent; NO_BUMP=1 NO_COMMIT=1 DRY_RUN=1 TESTERS= NOTES=
scripts/deeplink.sh 'myapp://profile?id=42' customer dev          # adb, one device; ids as the build computes them
scripts/deeplink.sh --ios 'myapp://profile?id=42'                # booted simulator
```

## Store lanes (the release pipeline)

`fastlane/Fastfile` holds the lanes the shared GitLab pipeline's mobile flow calls (README → Releasing):
`check_build_number`, `android dev_apk`, and per platform `beta_upload`, `prod_upload`, `promote_closed`,
`promote_open`, `promote_production`. They build through `make`; the pure-Ruby part is `fastlane/lib/release_helpers.rb`
with tests in `fastlane/test` (`make test-fastlane`). Ruby `.ruby-version` (3.4), bundler 2.6.6, `Gemfile.lock`.

- **Inputs are environment variables** (`fastlane/.env.example`): `APP` when there are several apps, `VERSION_NAME`
  (X.Y.Z), `BUILD_NUMBER`, `RELEASE_NOTES_FILE`, the Play and App Store Connect keys, the upload keystore
  (`ANDROID_KEYSTORE_FILE` / `ANDROID_KEYSTORE_PROPERTIES_FILE`, copied into `config/signing/release/<app>/` for
  the build), match (`MATCH_SSH_KEY_FILE` needs `MATCH_KNOWN_HOSTS`), TestFlight groups, Beta App Review.
- **The project's choices are `app.release.*` keys** in `gradle.properties`, each optional and overridable for one
  app as `app.release.<app>.<key>`: `devApk`, `beta`, `production` (channel → flavor), `closedTrack`, `rollout`,
  `internalGroup`, `notesLocale`, `iosBundleId`. Put a project's difference there — never in the Fastfile, which a
  kit update replaces.
- **Versions:** the lanes pass `-Papp.versionName` / `-Papp.versionCode` to Gradle and `MARKETING_VERSION` /
  `CURRENT_PROJECT_VERSION` to Xcode; `version.properties` is untouched by a release.
- **Play takes only production's id:** a flavor uploaded there needs `app.flavors.<flavor>.idSuffix=none`; the lane
  refuses another (it would be debug-signed).
- Promotions move the uploaded build; on Play only one found on its upload lane's track (`internal` for
  `promote_closed`, `production` for the others).
- Firebase App Distribution is firebase-kit's (`fastlane/FirebaseFastfile`, imported by the app); `make
  distribute-android` is its Android entry here.
