# build-kit

The Gradle build structure a Compose Multiplatform application starts from: convention plugins,
environment flavors, signing, and — as the kit grows — the Detekt rules and hooks, module tooling and
reports. An application takes it by copy (`skillctl.sh kit install build-kit`), renamed to its own
package, and merges later changes in; nothing here is published as a library.

**Status:** convention plugins and flavors are in place and proven by `fixture/`. Detekt, the
module tasks, reports, scripts, docs, skills and `kit.yml` follow.

## Conventions

| Plugin (`libs.plugins.buildkit.…`) | For |
|---|---|
| `kmp.library` | a multiplatform module: Android + iosArm64 + iosSimulatorArm64, namespace, test dependencies, iOS framework naming |
| `compose` | `kmp.library` + Compose Multiplatform, the stability configuration and opt-in compiler reports |
| `injection` | Metro code generation, on a multiplatform, JVM or Android application module |
| `kotlin.library` | a plain Kotlin/JVM module (tools, generators) |
| `android.application` | an Android app (`apps/<name>`): the `environment` flavors, per-flavor version codes, shared debug and production release signing |
| `environment` | generates `Environment` in `commonMain` from `.env.<active flavor>` |
| `modules` | root project only: rejects any `api(...)` dependency |

## Flavors

`gradle.properties` declares them — `app.flavors=regress,dev,beta,prod`, `app.flavors.production`,
`app.flavors.default` — at least two; `test…` names are refused by Android. Application modules get
real product flavors. Multiplatform modules have none, so each Gradle invocation builds shared code
for **one** flavor, chosen in this order: `-Papp.env`, the requested variant task names
(`assembleBetaDebug`), Xcode's `CONFIGURATION`, `app.env` in `local.properties`,
`app.flavors.default`. Flavor-scoped shared dependencies:
`flavorImplementation("regress", …)`, `productionImplementation(…)`, `nonProductionImplementation(…)`.

## Building

```sh
./gradlew build     # build-logic tests, every fixture module on Android host and iOS simulator
```

Needs JDK 21 for the Gradle daemon (Metro's Gradle plugin), provisioned automatically from
`gradle/gradle-daemon-jvm.properties`; modules still target the catalog's `jvm` version.
