---
name: build-kit
description: Works on the Gradle build of Compose Multiplatform apps that use build-kit, the convention-plugin kit (plugins io.thernal.buildkit.kmp.library, .android.library, .compose, .injection, .kotlin.library, .android.application, .environment, .modules, .detekt; libs.plugins.buildkit.*; app.* keys in gradle.properties). Use it for any build work in such a project, even when build-kit is not named - installing the kit, creating api/impl/wiring modules, adding an app or a flavor, .env values and the generated Environment object, flavor-only dependencies, signing and version codes, the Detekt pre-commit hook and "// TODO: Detekt" markers, "api(...) is not used" errors, module health and Compose stability reports, dependency analysis, make targets, deep-link testing and store lanes - and for failures such as "Requested tasks span several flavors", "contradicts the requested variant tasks" or a commit blocked by detekt. Not for Gradle builds without build-kit.
---

# build-kit

build-kit is the Gradle build of a Compose Multiplatform application (Android, iosArm64,
iosSimulatorArm64), or of an Android-only one (`app.platforms=android`): convention plugins in `build-logic/`, environment flavors shared by several apps,
Detekt with project rules and a block-then-annotate hook, module scaffolding, reports, and the scripts
around them. Source and the full guide: https://github.com/Thernal/build-kit — `README.md` for what and
how, `build-logic/README.md` for why.

## 1. Orient before changing the build

Most mistakes here add a second mechanism beside one build-kit already has. Look first:

```sh
grep -n '^app\.' gradle.properties                          # flavors, namespace, module areas, api exceptions
grep -rn 'libs.plugins.buildkit.' --include=build.gradle.kts . | sed 's/:.*alias/ alias/' | sort | uniq -c | sort -rn | head
ls apps/ .env.* 2>/dev/null                                  # applications and environment files
grep -n 'build-kit\|app-settings' settings.gradle.kts        # module discovery comes from gradle/app-settings.gradle.kts
ls report/ 2>/dev/null; git config core.hooksPath            # reports present? hook installed (.githooks)?
```

No `build-logic/` and no `libs.plugins.buildkit.` → not installed: read
[references/setup.md](references/setup.md) first.

**Taken as a kit?** A `kits.lock` naming `build-kit` means `build-logic/` and the rest were copied with
skill-manager, renamed to the project's package and alias — this skill with it, so the names here are
already the project's. `skillctl.sh kit status build-kit` says what moved upstream; offer
`kit update build-kit` rather than editing `build-logic/` towards a newer version by hand. Local edits to
`build-logic/` survive updates but conflict with every upstream change to the same lines — keep them few.

## 2. The model

- **Capabilities, not layers.** A module applies what it uses: `kmp.library` (every multiplatform
  module), `compose` (UI), `injection` (Metro), `kotlin.library` (JVM tools), `android.application`
  (each `apps/<name>/android`), `environment` (the one configuration module). Detekt comes with all of them.
  `compose` also packages Compose Resources into the Android library target (`androidResources`), so
  `Res` works on Android without anything in the module's own build file.
- **Android-only** (`app.platforms=android`): modules apply `android.library` (`kmp.library` fails);
  `compose` is Jetpack Compose from the AndroidX BOM, `environment` generates `Environment` into the
  library's sources; an app is one module, `apps/<name>`, with `version.properties` beside it.
  Libraries have no product flavors: `nonProductionImplementation(project, …)` in `dependencies {}`.
  Per module too: `android.library` named before `compose`/`environment` makes just that module Android.
- **Apps are symmetric:** `apps/<name>/{android,ios,shared}` — the Android module, the Xcode project
  (`ios/project.yml`) and the app's KMP root both embed; `version.properties` beside them. `APP=` in
  `make` and fastlane picks all three. A second app is a second such directory, never a second iosApp.
- **No `api(...)`.** Every dependency is `implementation`, declared where its types are used. The build
  fails otherwise. Only modules exported into the iOS framework (`export(...)`) go in `app.api.allowed`.
- **Flavors are data** (`app.flavors`). Apps have real product flavors; shared code is built for **one
  flavor per Gradle invocation** (`-Papp.env`, else the variant task names, Xcode `CONFIGURATION`,
  `local.properties`, the default). Environment values come from `.env.<flavor>` as `Environment.*`.
- **Detekt never fails the build.** The pre-commit hook blocks a commit whose findings are new, records
  them, marks them `// TODO: Detekt [...]`; the same commit then passes.
- **Reports are generated.** `report/` is written by `graph` and `composeStabilityReport`; never edit it.

## 3. Task router

| Task | Do | Read |
|---|---|---|
| install the kit, or fix a broken setup | settings, root build, catalog, properties | [setup.md](references/setup.md) |
| new capability or module | `./gradlew create <name> api impl wiring` | [modules-and-reports.md](references/modules-and-reports.md) |
| "declares api(...) dependencies" | `implementation` in every consumer; `app.api.allowed` only for framework exports | [modules-and-reports.md](references/modules-and-reports.md) |
| new flavor, new app, new `.env` key, flavor-only dependency | properties + files, no convention edits | [flavors.md](references/flavors.md) |
| flavor errors, wrong environment in a build | the detection order | [flavors.md](references/flavors.md) |
| commit blocked by detekt, markers, a rule to silence | fix, or commit again; `@Suppress` with a reason | [detekt.md](references/detekt.md) |
| module health, stability, unused dependencies | `graph`, `composeStabilityReport`, `buildHealth` | [modules-and-reports.md](references/modules-and-reports.md) |
| build an artifact, bump a version code, test a deep link, upload to a store | `make`, `scripts/`, `fastlane` | [modules-and-reports.md](references/modules-and-reports.md) |

## 4. Rules

- Never add `api(...)`, never add a flavor name to Kotlin code in `build-logic/`, never hard-code an
  environment value — `.env.<flavor>` exists for that.
- Never commit with `--no-verify` to get past the hook. Committing again passes once the findings are
  recorded; a finding that is wrong gets `@Suppress("Rule") // why` or a `detekt.yml` change.
- Never delete or hand-edit `// TODO: Detekt` markers to make a commit pass — the hook rewrites them;
  fix the code instead, and the marker disappears on the next run.
- Build one flavor per invocation: `./gradlew assembleBetaDebug assembleProdRelease` fails by design.
- Do not edit `report/`. Regenerate it.

## 5. Verify

```sh
./gradlew build                  # every module, Android host and iOS simulator tests, build-logic tests
./gradlew detektFull             # then: git diff --stat shows only intended markers, if any
./gradlew graph                  # when modules or their dependencies changed; commit report/
```
