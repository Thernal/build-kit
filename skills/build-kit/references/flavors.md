# Flavors, environment, apps

## The list

`gradle.properties`: `app.flavors` (ordered, at least one — a single flavor is production and default), `app.flavors.production`,
`app.flavors.default`. Names are lowercase letters and digits; `test…`, `main`, `debug`, `release`,
`lint` are refused (Android reserves them). The default set is `regress, dev, beta, prod`:
`regress` for autonomous regression runs (the real DI graph against a local mock server with recorded
fixtures; only clocks, push, permissions and analytics are faked), `dev` for developers, `beta` for manual
testing before a release, `prod` for production.

**Adding a flavor:** add it to `app.flavors`, create `.env.<flavor>`, add `<flavor>.versionCode=1` to each
`apps/*/version.properties` (optional — 1 is the default), and in each `apps/*/ios/project.yml` the
`<Flavor> Debug` / `<Flavor> Release` configurations (with its `BUNDLE_ID_SUFFIX` and
`CURRENT_PROJECT_VERSION: ${APP_VERSION_CODE_<FLAVOR>}`) and the
`<app>-<Flavor>-<BuildType>` schemes. No code
in `build-logic/` changes. **Removing** one is the reverse; search for `flavorImplementation("<name>"`.

## Which flavor a build uses

Application modules build every variant they are asked for. Shared (multiplatform) code is compiled for
one flavor per Gradle invocation, the first of:

1. `-Papp.env=<flavor>`
2. the flavor in the requested task names — `assembleBetaDebug`, `bundleProd`, `installDev`
3. Xcode's `CONFIGURATION` (`Beta Release` → `beta`) — the iOS framework build phase
4. `app.env=<flavor>` in `local.properties`
5. `app.flavors.default`

| Error | Meaning |
|---|---|
| `Requested tasks span several flavors (dev, prod)` | two variants in one command — run them separately |
| `app.env='beta' contradicts the requested dev variant tasks` | drop `-Papp.env` or build the matching variant |
| `Unknown flavor 'x' in …` | not in `app.flavors` |

A plain `./gradlew build` builds every application variant but shared code only for the default flavor —
use it for checks, not for artifacts; artifacts come from `make build-android FLAVOR=…`.

## Environment values

`.env.<flavor>` at the root, `UPPER_SNAKE_KEY=value` per line, `#` comments. The module that applies
`libs.plugins.<alias>.environment` (one, owning configuration) gets, in `commonMain`:

```kotlin
package <its namespace>          // override: environment { packageName.set("…"); objectName.set("…") }
public object Environment {
    public const val FLAVOR: String
    public const val IS_PRODUCTION: Boolean
    public const val BASE_URL: String   // one per key
}
```

Every flavor has every key (empty, with a warning, where a file lacks it), so code compiles for all.
`FLAVOR` and `IS_PRODUCTION` are reserved. Secrets do not go in `.env` files that are committed.

## Flavor-scoped dependencies

In a multiplatform module (`import io.thernal.buildkit.buildlogic.flavor.*` at the top of the build file):

```kotlin
commonMain.dependencies {
    nonProductionImplementation(projects.core.debugConsole)
    productionImplementation(libs.console.noop)
    flavorImplementation("regress", "dev", dependencyNotation = projects.core.fakes)
}
```

The dependency is absent from every classpath of the other flavors — code that references it must itself
be flavor-scoped (a DI binding contributed from the flavor-only module, not a direct call). In an
application module use AGP's own `regressImplementation(...)`, `prodImplementation(...)`.

## Applications

Each `apps/<name>/` is an app — `android/` (with `libs.plugins.<alias>.android.application`), `ios/`
(the Xcode project) and `shared/` (its KMP root). Per flavor: `versionCode` from the app's
`version.properties` (production's for an `idSuffix=none` flavor; `-Papp.versionCode` / `-Papp.versionName`
override both for a store release), `.<flavor>` application id suffix and `-<flavor>` version name suffix off
production, and every non-production variant is signed so a tester's build installs: with the shared debug
keystore when `config/signing/debug/` exists, else with AGP's debug key. The
production `release` variant is signed from `config/signing/release/<app>/` when the app has its own keystore, else
from `config/signing/release/` (both never committed); without one it stays unsigned unless
`-PrequireReleaseSigning=true`.

`app.flavors.<flavor>.idSuffix=<suffix>` gives a flavor another suffix (a staging build on the dev app);
`idSuffix=none` gives it production's id — a beta testers install over the store app — and its release is
signed with the production key like production's. iOS mirrors the suffix: each flavor's configurations set
`BUNDLE_ID_SUFFIX` (`.dev`; nothing for production and an `idSuffix=none` flavor) and
`PRODUCT_BUNDLE_IDENTIFIER` is the base id plus it. Firebase needs one app per distinct id.

Set the real id in the app's own build file:

```kotlin
android { defaultConfig { applicationId = "com.example.customer" } }
```

A new app is a new `apps/<name>/` with all three parts and `version.properties` — copy
`fixture/apps/partner`; the scripts and `make` find it by `android/build.gradle.kts`.
