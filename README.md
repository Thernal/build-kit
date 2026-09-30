# build-kit

The Gradle build structure a Compose Multiplatform application starts from: convention plugins,
environment flavors, signing, and — as the kit grows — the Detekt rules and hooks, module tooling and
reports. An application takes it by copy (`skillctl.sh kit install build-kit`), renamed to its own
package, and merges later changes in; nothing here is published as a library.

**Status:** convention plugins, flavors and the Detekt workflow are in place and proven by
`fixture/`. The module tasks, reports, scripts, docs, skills and `kit.yml` follow.

## Conventions

| Plugin (`libs.plugins.buildkit.…`) | For |
|---|---|
| `kmp.library` | a multiplatform module: Android + iosArm64 + iosSimulatorArm64, namespace, test dependencies, iOS framework naming |
| `compose` | `kmp.library` + Compose Multiplatform, the stability configuration and opt-in compiler reports |
| `injection` | Metro code generation, on a multiplatform, JVM or Android application module |
| `kotlin.library` | a plain Kotlin/JVM module (tools, generators) |
| `android.application` | an Android app (`apps/<name>`): the `environment` flavors, per-flavor version codes, shared debug and production release signing |
| `environment` | generates `Environment` in `commonMain` from `.env.<active flavor>` |
| `modules` | root project only: rejects any `api(...)` dependency, except the modules exported into the iOS framework and listed in `app.api.allowed` |

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

## Building

```sh
./gradlew build     # build-logic tests, every fixture module on Android host and iOS simulator
```

Needs JDK 21 for the Gradle daemon (Metro's Gradle plugin), provisioned automatically from
`gradle/gradle-daemon-jvm.properties`; modules still target the catalog's `jvm` version.
