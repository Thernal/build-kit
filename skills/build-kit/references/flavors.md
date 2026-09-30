# Flavors, environment, apps

## The list

`gradle.properties`: `app.flavors` (ordered, at least two), `app.flavors.production`,
`app.flavors.default`. Names are lowercase letters and digits; `test…`, `main`, `debug`, `release`,
`lint` are refused (Android reserves them). The default set is `regress, dev, beta, prod`:
`regress` for autonomous regression runs (the real DI graph against a local mock server with recorded
fixtures; only clocks, push, permissions and analytics are faked), `dev` for developers, `beta` for manual
testing before a release, `prod` for production.

**Adding a flavor:** add it to `app.flavors`, create `.env.<flavor>`, add `<flavor>.versionCode=1` to each
`apps/*/version.properties` (optional — 1 is the default), and in Xcode add the `<Flavor> Debug` /
`<Flavor> Release` configurations and an `iosApp-<Flavor>-<BuildType>` scheme if iOS builds it. No code
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

In a multiplatform module (`import io.thernal.buildkit.buildlogic.*` at the top of the build file):

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

Each `apps/<name>/` with `libs.plugins.<alias>.android.application` is an app. Per flavor: `versionCode`
from `version.properties`, `.<flavor>` application id suffix and `-<flavor>` version name suffix off
production, the shared debug signature off production (when `config/signing/debug/` exists). The
production `release` variant is signed from `config/signing/release/` (never committed); without it it
stays unsigned unless `-PrequireReleaseSigning=true`. Set the real id in the app's own build file:

```kotlin
android { defaultConfig { applicationId = "com.example.customer" } }
```

A new app is a new directory under `apps/` with that build file, a manifest and `version.properties`;
the scripts and `make` find it by its build file.
