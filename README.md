# build-kit

The Gradle build a Compose Multiplatform application (Android + iOS) — or an Android-only one — starts
from: convention plugins,
environment flavors for several apps, signing, Detekt with the project's own rules and a
block-then-annotate pre-commit hook, module scaffolding, module and Compose stability reports,
dependency analysis, and the scripts, `make` targets and store lanes around them.

An application takes it **by copy**, not as a dependency — see [Installing](#installing). Nothing is
published to a Maven repository.

## Documentation

| Read | For |
|---|---|
| this file | what the kit does and how to use it |
| [`build-logic/README.md`](build-logic/README.md) | why each part has its shape — the decisions and their trade-offs |
| [`skills/build-kit`](skills/build-kit/SKILL.md) | the same, for an agent working in an application that took the kit |
| [`kit.yml`](kit.yml) | what an application copies, and what its build must provide |

## Installing

An application takes the kit **by copy**, not as a dependency: the code is copied into the app, renamed to the app's own package, and belongs to the app from then on. Nothing is published to a Maven repository.

### With skill-manager

If you have access to the author's knowledge repository (`github.com/Thernal/knowledge`), its **skill-manager** skill does all of it — copy, rename, the skill, and later updates:

```sh
skillctl.sh kit install build-kit --package com.example.app --alias app
```

It copies the `code` parts of [`kit.yml`](kit.yml) renamed, installs the `build-kit` skill and records the copy in `kits.lock`. `kit status` then shows what changed upstream and what the app edited; `kit update` merges the kit's changes three ways, keeping the app's edits. The install prints what the app must provide (`requires`).

### Without it

The same by hand, from a clone of this repository.

1. **Copy** the paths listed under `code` in [`kit.yml`](kit.yml) into the app, at the same paths. Note the commit you copied (`git rev-parse HEAD`) — updates start from it.
2. **Rename** in everything copied:

   | In the kit | Becomes | Where |
   |---|---|---|
   | `io.thernal.buildkit` | the app's package, e.g. `com.example.app` | sources, build files; and the directories `io/thernal/buildkit` |
   | `libs.plugins.buildkit.` | the app's catalog alias, e.g. `libs.plugins.app.` | build files |

   ```sh
   # in the app, after copying — perl, so it runs the same on macOS and Linux
   grep -rlI -e io.thernal.buildkit -e io/thernal/buildkit -e plugins.buildkit. .githooks Makefile build-logic config/detekt config/signing/keystore.properties.example fastlane gradle/app-settings.gradle.kts scripts \
     | xargs perl -pi -e 's/\Qio.thernal.buildkit\E/com.example.app/g; s{\Qio/thernal/buildkit\E}{com/example/app}g; s/libs\.plugins\.\Qbuildkit\E\./libs.plugins.app./g'
   find .githooks Makefile build-logic config/detekt config/signing/keystore.properties.example fastlane gradle/app-settings.gradle.kts scripts -depth -type d -path '*/io/thernal/buildkit' | while read -r d; do
     mkdir -p "${d%/io/thernal/buildkit}/com/example" && mv "$d" "${d%/io/thernal/buildkit}/com/example/app"
   done
   find .githooks Makefile build-logic config/detekt config/signing/keystore.properties.example fastlane gradle/app-settings.gradle.kts scripts -depth -type d -empty -delete
   ```

3. **Provide** what the copy expects — the `requires` list in [`kit.yml`](kit.yml): settings, the root build file, the version catalog and `gradle.properties` keys — [`skills/build-kit/references/setup.md`](skills/build-kit/references/setup.md) walks through them, and `fixture/` is a working example.
4. **The skill** (optional): copy [`skills/build-kit`](skills/build-kit) into the app's skills directory (`.claude/skills/` for Claude Code), with the same renames, so an agent working in the app knows the kit.
5. **Updates** are yours to carry: `git diff <the commit you copied> <a newer one> -- <the code paths>` in the kit shows what changed; apply what you want, renamed the same way.

## Apps

Every app is `apps/<name>/` with the same three parts, so Android and iOS stay symmetric however many
apps the repository ships:

```text
apps/<name>/
  android/            :apps:<name>:android — the Android application (`android.application`)
  ios/                the Xcode project, generated from ios/project.yml (xcodegen)
  shared/             :apps:<name>:shared — the app's Kotlin Multiplatform root (its graph, its root
                      composable); android/ depends on it, ios/ embeds its framework
  version.properties  versionName and one <flavor>.versionCode — the app's, for both platforms
```

**Versions.** `version.properties` is read by the Android build and, through `make xcode-project`
(`scripts/xcode-project.sh`), by the Xcode project: xcodegen expands `${APP_VERSION_NAME}` and
`${APP_VERSION_CODE_<FLAVOR>}` in `project.yml` into `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` per
configuration, and `Info.plist` reads those. A project generated by plain `xcodegen` has no version, and its
framework build phase stops and says so. Each flavor counts on its own, since each is its own store
application — except a flavor on production's id (`idSuffix=none`), which is production's application and counts
with production's code. A store release takes neither: the pipeline passes the version and build number, which
win over the file (`-Papp.versionName`, `-Papp.versionCode`; Xcode's `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION`);
see [Releasing](#releasing).

Xcode configurations are named after the flavor (`Beta Debug`, `Beta Release`) and schemes
`<name>-<Flavor>-<BuildType>`; each configuration sets `KOTLIN_FRAMEWORK_BUILD_TYPE`, since Kotlin cannot
read the build type from a flavored name, and `PRODUCT_BUNDLE_IDENTIFIER` with the flavor's id suffix,
the same as Android's (`.dev`; none for production and an `idSuffix=none` flavor). `fixture/apps/customer` and `fixture/apps/partner` are complete
examples of all three parts.

## Conventions

| Plugin (`libs.plugins.<alias>.…`) | Applied to | Does |
|---|---|---|
| `kmp.library` | every multiplatform module | Kotlin Multiplatform with Android, iosArm64, iosSimulatorArm64; Android namespace from `app.namespace` + module path (below `app.modules.root`); test dependencies; static iOS frameworks named after the module; Detekt |
| `android.library` | every Android-only module | a plain Android library: namespace from `app.namespace` + module path (below `app.modules.root`), SDKs and JVM from the catalog, test dependencies, Android pipelines off until a module turns one on, Detekt |
| `compose` | UI modules | `kmp.library` + Compose Multiplatform — or, on an Android library, Jetpack Compose from the AndroidX BOM — with runtime, foundation, ui and the `@Preview` annotations as `implementation`, the preview tooling on the module's Android runtime classpath (debug only on Android), the stability configuration, compiler metrics on demand, the module's stability report task |
| `injection` | modules with Metro code | Metro code generation and runtime, on multiplatform, JVM or Android application modules |
| `graph` | the module that declares the Metro graph: each `apps/<name>/shared`, or `apps/<name>` in an Android-only app | `injection`, and every `…:wiring` module outside `apps` as a dependency, minus the paths in `app.graph.exclude` and `app.graph.<name>.exclude` — the app's build file names only what its own code imports |
| `kotlin.library` | JVM-only tools | Kotlin/JVM, test dependencies, Detekt |
| `android.application` | each `apps/<name>/android`, or `apps/<name>` in an Android-only app | the environment flavors, per-flavor version codes from the app's `version.properties`, id and name suffixes, shared debug and production release signing, R8, Compose, Detekt |
| `environment` | the one module that owns configuration | generates `Environment` from `.env.<active flavor>` — in `commonMain`, or in an Android library's sources |
| `modules` | the root project | rejects `api(...)` (except `app.api.allowed`), `create`, `graph`, the stability index, dependency analysis |
| `detekt` | the root project | the Detekt pipeline tasks and the Git hooks |

A module names only the capabilities it uses:

```kotlin
plugins {
    alias(libs.plugins.app.compose)
    alias(libs.plugins.app.injection)
}
```

Opt-ins are the application's, not a module's: `app.kotlin.optIn` in `gradle.properties` (comma-separated,
`kotlin.time.ExperimentalTime,kotlinx.coroutines.ExperimentalCoroutinesApi`) reaches every Kotlin compilation of
every convention, so no file repeats `@OptIn` for them.

Every Kotlin compilation also runs the unused return value checker (`-Xreturn-value-checker=check`, off by
default in Kotlin 2.4): a dropped value of a `@MustUseReturnValues` declaration — a repository returning `Result`,
arch-kit's `safeCall` — or of most of the standard library is a compiler warning. `val _ = call()` drops one on
purpose (conventions-kit rule `kotlin-features`).

### Android-only applications

`app.platforms=android` in `gradle.properties` builds an application with no iOS app and no shared
code:

- modules apply `android.library` (`./gradlew create` scaffolds it, with `src/main`), and
  `kmp.library` fails with a message saying so;
- `compose` and `environment` take the Android path: Jetpack Compose from the AndroidX Compose BOM,
  `Environment` generated into the library's sources — read exactly as in a multiplatform app;
- each app is a single module, `apps/<name>`, with `version.properties` beside its build file;
- libraries have no product flavors, as multiplatform modules do not: shared code is built for one
  flavor per invocation, and a flavor-scoped dependency is
  `nonProductionImplementation(project, …)` / `productionImplementation(project, …)` /
  `flavorImplementation(project, "regress", dependencyNotation = …)` in a plain `dependencies {}`.

The choice is per module, too: a multiplatform repository can hold an Android-only module by naming
`android.library` before `compose` or `environment` (`fixture/core/platform`, `fixture/apps/kiosk`).
`app.flavors.<flavor>.idSuffix=<suffix>` gives a non-production flavor another application id suffix
— two flavors sharing one installed identity. `idSuffix=none` gives it production's id: a beta that
testers install over the production app; its release is signed with the production key.

### No `api(...)`

Every dependency is `implementation`; a module that uses a type declares where it comes from. A
configuration-time check fails any `api(...)` — in every form, `commonMainApi` included. The one
exception is Kotlin/Native `export(...)`, which only accepts `api` dependencies: a module exported into
the iOS framework (so Swift sees its types) is listed in `app.api.allowed`.

## Flavors

Declared once, in `gradle.properties`:

```properties
app.flavors=regress,dev,beta,prod
app.flavors.production=prod
app.flavors.default=dev
```

| Flavor | Is |
|---|---|
| `regress` | autonomous regression runs: the real graph against a local mock server with recorded fixtures |
| `dev` | what a developer runs |
| `beta` | real manual testing before a release |
| `prod` | production |

At least one — an app with a single environment lists just `prod`, and its variants stay
`prodDebug`/`prodRelease`; any names (`test…` is refused — Android reserves it). Adding one is a list entry and its
`.env.<flavor>` file. Application modules get real product flavors: non-production ones install beside
production (`.dev` id suffix, `-dev` version name, shared debug signature). Multiplatform modules have
no product flavors, so **each Gradle invocation builds shared code for one flavor**, taken from, in
order: `-Papp.env=<flavor>`, the requested variant tasks (`assembleBetaDebug`), Xcode's `CONFIGURATION`
(`Beta Release`), `app.env=` in `local.properties`, `app.flavors.default`. Two flavors in one invocation,
or a `-Papp.env` that contradicts the tasks, fail.

`.env.<flavor>` files at the repository root (`KEY=value`) become constants:

```kotlin
Environment.FLAVOR          // "beta"
Environment.IS_PRODUCTION   // false
Environment.BASE_URL        // from .env.beta
```

Every flavor exposes every key, so code compiles whichever is built. Shared code can depend on
something per flavor:

```kotlin
commonMain.dependencies {
    nonProductionImplementation(projects.core.debugConsole)
    productionImplementation(libs.console.noop)
    flavorImplementation("regress", dependencyNotation = projects.core.fakes)
}
```

## Detekt

Every module runs Detekt with `config/detekt/detekt.yml` and the rules in `build-logic/detekt-rules`:
`LayerPackageRequired` and `LayerPackageBoundary` (`api`/`impl` code lives in `data`, `domain` or
`presentation` — or directly in a module named after its layer, `core/presentation/api`, never in
that layer's package again — and layers depend one way), `ExpressionBodyNotAllowed`, `MultilineConstructorRequired`,
`PreviewMustBePrivate`, `UnsafeCollectionIndexAccess`, plus ktlint.

**Findings never fail the build. The pre-commit hook decides:**

1. It analyses the changed Kotlin files, ktlint fixing formatting in place.
2. A finding not yet in `.misc/detekt/detekt-report.xml` **blocks the commit** — and is recorded there
   and marked on its line: `// TODO: Detekt [Rule: message]`.
3. Committing again passes. The finding is known now, and stays marked until someone fixes it.

`./gradlew detektFull` does the same for the whole repository. The first Gradle sync points Git at
`.githooks/` (not on CI).

Code another kit installed (the `part` lines in `kits.lock`; `map module` targets in an older lock) is
not analysed: that kit's own build holds
it to its rules, and markers written into it would turn every kit update into a merge conflict.

## Module tooling and reports

| Command | Does |
|---|---|
| `./gradlew create profile api impl wiring` | `features/profile/{api,impl,wiring}`: multiplatform modules, sibling dependencies, a Metro binding container (`<Name>ProvidersModule`) in `wiring` |
| `./gradlew create core/network api impl` | the same under another area |
| `./gradlew create core/common` | one module, not split into roles |
| `./gradlew graph` | `report/`: health verdict per module, build waves, dependency graphs, coupling metrics |
| `./gradlew assembleDevDebug -PcomposeStabilityReport=true` then `./gradlew composeStabilityReport` | `report/compose-stability/`: one page per Compose module (composables that cannot skip, unstable classes) and an index |
| `./gradlew buildHealth` | dependency-analysis advice, never a failure |

`app.modules.root` and `app.modules.areas` say where modules live; the root is a directory, not part
of any package — `fixture/features/greeting/impl` is `<app.namespace>.features.greeting.impl`. A
stability page changes only when its module's compiler output does, so branches touching different
modules do not conflict on them; `report/**/*.md` merges keeping one side, and the post-merge hook
regenerates the module report.

## Scripts, make, fastlane

```sh
make build-android APP=customer FLAVOR=beta BUILD_TYPE=release   # apk, or aab for production release
make build-ios-framework APP=customer FLAVOR=prod              # the app's shared/ framework
make build-ios-unsigned APP=customer FLAVOR=dev BUILD_TYPE=debug # simulator, no signing
make verify-android-release APP=customer FLAVOR=prod             # R8 on, mapping present
make xcode-project APP=customer                                  # ios/project.yml → .xcodeproj, with the version
make test test-ios test-fastlane detekt graph
scripts/bump-version-code.sh customer beta
scripts/deeplink.sh 'app://profile?id=42' customer dev           # adb; --ios <url> for the simulator
APP=customer bundle exec fastlane android beta_upload            # store lanes: see Releasing
```

All of them read flavors and apps from `gradle.properties`. Firebase App Distribution belongs to firebase-kit;
its Android path has a make target here, `make distribute-android APP=customer FLAVOR=beta TESTER_GROUPS=qa`
(it calls firebase-kit's `scripts/distribute-firebase.sh`, and says so when that is not installed). Each build it
sends gets the flavor's `versionCode` raised by one first, and once it is sent the version file is committed alone, so
the number testers have is the number on record; `NO_COMMIT=1` leaves it uncommitted, `NO_BUMP=1` keeps it. A non-production
variant is signed with the shared debug keystore when `config/signing/debug/` exists, else with AGP's debug key,
so the APK a tester receives always installs.

Credentials never enter the repository; examples of their shape do:

| Example | Real file | Holds |
|---|---|---|
| `fastlane/.env.example` | `fastlane/.env` (git-ignored), or CI variables | the store lanes' inputs: app, version, notes, Play and App Store Connect keys, upload keystore, match, TestFlight groups |
| `config/signing/keystore.properties.example` | `config/signing/debug/keystore.properties` (committed, with the shared debug keystore) and `config/signing/release/keystore.properties`, or one app's own in `config/signing/release/<app>/` (git-ignored; CI writes it) | the keystore file, alias and passwords |

## Releasing

`fastlane/Fastfile` holds the lanes a release pipeline calls — the shared GitLab pipeline's mobile flow does
(`devops/infraops/pipeline`, README → Mobile apps): a beta channel for testers and a production channel, each
uploaded once and then promoted. Ruby comes from `.ruby-version`, the gems from `Gemfile.lock` (bundler 2.6.6).

| Lane | Does |
|---|---|
| `check_build_number` | fails unless `BUILD_NUMBER` is above every build of the app in Play (all tracks) and App Store Connect, the version is `X.Y.Z` and the notes fit Google Play |
| `android dev_apk` | the dev flavor's release APK, signed with the debug key, to `$ARTIFACT_PATH/android/<app>-<flavor>-release.apk` |
| `android\|ios beta_upload` | the beta flavor to Play internal testing / TestFlight's internal group |
| `android\|ios prod_upload` | the production flavor to Play production as a draft / TestFlight without a group |
| `android\|ios promote_closed` | the beta build to closed testing (Play `closedTrack` / `TESTFLIGHT_CLOSED_GROUP`, Beta App Review) |
| `android\|ios promote_open` | the production build to open testing (Play `beta` / `TESTFLIGHT_OPEN_GROUP`) |
| `android\|ios promote_production` | Play: staged rollout at `rollout`; App Store: submitted for review, released by hand |

Inputs are environment variables (`fastlane/.env.example` lists them): `APP` (optional with one app), `VERSION_NAME`,
`BUILD_NUMBER`, the notes and the store keys. The version reaches both builds through the overrides above; a
promotion moves the build already uploaded, and on Play only one found on the track its upload lane put it on.

What is the project's is data, in `gradle.properties` — every key optional, for all apps (`app.release.<key>`)
or one (`app.release.<app>.<key>`), so a kit update never changes it:

| Key | Default | Is |
|---|---|---|
| `devApk` | `dev`, else `app.flavors.default` | the flavor of the dev APK |
| `beta` | `beta` when listed | the flavor of the beta channel; without one, the beta lanes fail and say so |
| `production` | `app.flavors.production` | the flavor of the production channel |
| `closedTrack` | `alpha` | Play's closed testing track |
| `rollout` | `0.01` | the first staged rollout fraction on Play production |
| `internalGroup` | `Internal Testers` | the TestFlight group of `beta_upload` |
| `notesLocale` | `en-US` | the language of the notes in both stores |
| `iosBundleId` | `PRODUCT_BUNDLE_IDENTIFIER` in `ios/project.yml` | production's bundle id; flavors add their id suffix |

The Android package is the app module's `applicationId` with the flavor's suffix. A flavor that goes to Play must
have production's id (`app.flavors.<flavor>.idSuffix=none`): only those are signed with the upload key. The dev APK
is signed with `config/signing/debug/` when it exists — commit it, or every CI build gets a new debug signature
and testers have to uninstall before each update.

## This repository

`fixture/` is a small application built only from these conventions — two apps, an `api`/`impl`/`wiring`
feature, a generated environment, a non-production module, an iOS framework export, a JVM tool — and
is how every part is proven. It is not copied into applications.

```sh
./gradlew build      # build-logic and Detekt rule tests; every fixture module on the Android host and the iOS simulator
```

The Gradle daemon runs on JDK 21 (Metro's Gradle plugin), provisioned from
`gradle/gradle-daemon-jvm.properties`; modules target the catalog's `jvm` version.
