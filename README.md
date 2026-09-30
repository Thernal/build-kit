# build-kit

The Gradle build structure a Compose Multiplatform application starts from: convention plugins,
environment flavors, signing, and — as the kit grows — the Detekt rules and hooks, module tooling and
reports. An application takes it by copy (`skillctl.sh kit install build-kit`), renamed to its own
package, and merges later changes in; nothing here is published as a library.

**Status:** conventions, flavors, the Detekt workflow, module tooling, reports and scripts are in
place and proven by `fixture/`. Full docs, skills and `kit.yml` follow.

## Conventions

| Plugin (`libs.plugins.buildkit.…`) | For |
|---|---|
| `kmp.library` | a multiplatform module: Android + iosArm64 + iosSimulatorArm64, namespace, test dependencies, iOS framework naming |
| `compose` | `kmp.library` + Compose Multiplatform, the stability configuration and opt-in compiler reports |
| `injection` | Metro code generation, on a multiplatform, JVM or Android application module |
| `kotlin.library` | a plain Kotlin/JVM module (tools, generators) |
| `android.application` | an Android app (`apps/<name>`): the `environment` flavors, per-flavor version codes, shared debug and production release signing |
| `environment` | generates `Environment` in `commonMain` from `.env.<active flavor>` |
| `modules` | root project only: rejects any `api(...)` dependency except those in `app.api.allowed` (iOS framework exports); `create`, `graph`, `composeStabilityReport`; dependency-analysis when the root declares it |
| `detekt` | root project only: the Detekt pipeline tasks and the Git hooks |

## Flavors

`gradle.properties` declares them — `app.flavors=regress,dev,beta,prod`, `app.flavors.production`,
`app.flavors.default` — at least two; `test…` names are refused by Android. Application modules get
real product flavors. Multiplatform modules have none, so each Gradle invocation builds shared code
for **one** flavor, chosen in this order: `-Papp.env`, the requested variant task names
(`assembleBetaDebug`), Xcode's `CONFIGURATION`, `app.env` in `local.properties`,
`app.flavors.default`. Flavor-scoped shared dependencies:
`flavorImplementation("regress", …)`, `productionImplementation(…)`, `nonProductionImplementation(…)`.

## Detekt

Every module gets Detekt with the custom rules in `build-logic/detekt-rules` and
`config/detekt/detekt.yml`. Findings never fail the build; the pre-commit hook decides:

1. It analyses the changed Kotlin files (`detektAnalysis`, ktlint auto-correct on).
2. A finding that is not yet in `.misc/detekt/detekt-report.xml` **blocks the commit** — and is merged
   into that report and marked in the source as `// TODO: Detekt [Rule: message]`.
3. Committing again passes: the findings are known now, and stay marked until someone fixes them.

`./gradlew detektFull` does the same for the whole repository and replaces the report. The hook is
installed by the first Gradle sync (`core.hooksPath = .githooks`), except on CI.

## Module tooling and reports

| Command | Does |
|---|---|
| `./gradlew create profile api impl wiring` | scaffolds `features/profile/{api,impl,wiring}` — multiplatform modules, sibling dependencies, a Metro binding container in `wiring` |
| `./gradlew graph` | writes `report/`: module health verdicts, build waves, dependency graphs, coupling metrics |
| `./gradlew assembleDevDebug -PcomposeStabilityReport=true` then `./gradlew composeStabilityReport` | one page per Compose module under `report/compose-stability/` (non-skippable composables, unstable classes) and an index; a module's page changes only when its own code does |
| `./gradlew buildHealth` | dependency-analysis advice (never fails) |

`app.modules.root` and `app.modules.areas` say where modules live. `report/*.md` merges with a
driver that keeps one side, and `.githooks/post-merge` regenerates the module report afterwards. The
stability pages are per module, so branches touching different modules do not conflict on them.

## Scripts, make and fastlane

`make build-android APP=customer FLAVOR=beta`, `make build-ios-framework`, `make verify-android-release`,
`make test`, … — flavors and apps are read from `gradle.properties`. `scripts/bump-version-code.sh
<app> <flavor>`, `scripts/deeplink.sh <url> [app] [flavor]` (or `--ios <url>`). `fastlane/Fastfile`
has the store lanes (Play, TestFlight); Firebase distribution belongs to firebase-kit.

## Building

```sh
./gradlew build     # build-logic tests, every fixture module on Android host and iOS simulator
```

Needs JDK 21 for the Gradle daemon (Metro's Gradle plugin), provisioned automatically from
`gradle/gradle-daemon-jvm.properties`; modules still target the catalog's `jvm` version.
