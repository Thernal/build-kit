# build-kit

The Gradle build a Compose Multiplatform application (Android + iOS) starts from: convention plugins,
environment flavors for several apps, signing, Detekt with the project's own rules and a
block-then-annotate pre-commit hook, module scaffolding, module and Compose stability reports,
dependency analysis, and the scripts, `make` targets and store lanes around them.

An application takes it **by copy**, not as a dependency: `skillctl.sh kit install build-kit` copies
`build-logic/` and the rest renamed to the application's package, installs the `build-kit` skill, and
records both in `kits.lock`, so every later change here is offered to the application and merged three
ways around its own edits. Nothing is published to a Maven repository.

## Documentation

| Read | For |
|---|---|
| this file | what the kit does and how to use it |
| [`build-logic/README.md`](build-logic/README.md) | why each part has its shape — the decisions and their trade-offs |
| [`skills/build-kit`](skills/build-kit/SKILL.md) | the same, for an agent working in an application that took the kit |
| [`kit.yml`](kit.yml) | what an application copies, and what its build must provide |

## Installing into an application

```sh
skillctl.sh kit install build-kit --package com.example.app --alias app
```

`--package` renames `io.thernal.buildkit` (plugin ids, sources, the Detekt rules' package prefix);
`--alias` renames `libs.plugins.buildkit.` in the copied files. The install prints what the
application's own files must provide — the `requires` list in `kit.yml`: settings, root build file,
version catalog, `gradle.properties` keys. [`skills/build-kit/references/setup.md`](skills/build-kit/references/setup.md)
walks through them with a complete example; `fixture/` is a working one.

## Conventions

| Plugin (`libs.plugins.<alias>.…`) | Applied to | Does |
|---|---|---|
| `kmp.library` | every multiplatform module | Kotlin Multiplatform with Android, iosArm64, iosSimulatorArm64; Android namespace from `app.namespace` + module path; test dependencies; static iOS frameworks named after the module; Detekt |
| `compose` | UI modules | `kmp.library` + Compose Multiplatform (runtime, foundation, ui as `implementation`), the stability configuration, compiler metrics on demand, the module's stability report task |
| `injection` | modules with Metro code | Metro code generation and runtime, on multiplatform, JVM or Android application modules |
| `kotlin.library` | JVM-only tools | Kotlin/JVM, test dependencies, Detekt |
| `android.application` | each `apps/<name>` | the environment flavors, per-flavor version codes from `version.properties`, id and name suffixes, shared debug and production release signing, R8, Compose, Detekt |
| `environment` | the one module that owns configuration | generates `Environment` in `commonMain` from `.env.<active flavor>` |
| `modules` | the root project | rejects `api(...)` (except `app.api.allowed`), `create`, `graph`, the stability index, dependency analysis |
| `detekt` | the root project | the Detekt pipeline tasks and the Git hooks |

A module names only the capabilities it uses:

```kotlin
plugins {
    alias(libs.plugins.app.compose)
    alias(libs.plugins.app.injection)
}
```

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

At least two; any names (`test…` is refused — Android reserves it). Adding one is a list entry and its
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
`presentation`, and layers depend one way), `ExpressionBodyNotAllowed`, `MultilineConstructorRequired`,
`PreviewMustBePrivate`, `UnsafeCollectionIndexAccess`, plus ktlint.

**Findings never fail the build. The pre-commit hook decides:**

1. It analyses the changed Kotlin files, ktlint fixing formatting in place.
2. A finding not yet in `.misc/detekt/detekt-report.xml` **blocks the commit** — and is recorded there
   and marked on its line: `// TODO: Detekt [Rule: message]`.
3. Committing again passes. The finding is known now, and stays marked until someone fixes it.

`./gradlew detektFull` does the same for the whole repository. The first Gradle sync points Git at
`.githooks/` (not on CI).

## Module tooling and reports

| Command | Does |
|---|---|
| `./gradlew create profile api impl wiring` | `features/profile/{api,impl,wiring}`: multiplatform modules, sibling dependencies, a Metro binding container in `wiring` |
| `./gradlew create core/network api impl` | the same under another area |
| `./gradlew graph` | `report/`: health verdict per module, build waves, dependency graphs, coupling metrics |
| `./gradlew assembleDevDebug -PcomposeStabilityReport=true` then `./gradlew composeStabilityReport` | `report/compose-stability/`: one page per Compose module (composables that cannot skip, unstable classes) and an index |
| `./gradlew buildHealth` | dependency-analysis advice, never a failure |

`app.modules.root` and `app.modules.areas` say where modules live. A stability page changes only when
its module's compiler output does, so branches touching different modules do not conflict on them;
`report/**/*.md` merges keeping one side, and the post-merge hook regenerates the module report.

## Scripts, make, fastlane

```sh
make build-android APP=customer FLAVOR=beta BUILD_TYPE=release   # apk, or aab for production release
make build-ios-framework FLAVOR=prod
make verify-android-release APP=customer FLAVOR=prod             # R8 on, mapping present
make test test-ios detekt graph
scripts/bump-version-code.sh customer beta
scripts/deeplink.sh 'app://profile?id=42' customer dev           # adb; --ios <url> for the simulator
bundle exec fastlane android play app:customer                   # Play internal track
bundle exec fastlane ios testflight flavor:beta
```

All of them read flavors and apps from `gradle.properties`. Firebase App Distribution is not here —
it belongs to firebase-kit.

## This repository

`fixture/` is a small application built only from these conventions — two apps, an `api`/`impl`/`wiring`
feature, a generated environment, a non-production module, an iOS framework export, a JVM tool — and
is how every part is proven. It is not copied into applications.

```sh
./gradlew build      # build-logic and Detekt rule tests; every fixture module on the Android host and the iOS simulator
```

The Gradle daemon runs on JDK 21 (Metro's Gradle plugin), provisioned from
`gradle/gradle-daemon-jvm.properties`; modules target the catalog's `jvm` version.
