# Installing build-kit

```sh
skillctl.sh kit install build-kit --package com.example.app --alias app
```

Without skill-manager, the kit's `README.md` → Installing → *Without it* does the same by hand (copy, rename, provide).

This copies, renamed: `build-logic/` (conventions, Detekt rules, their tests), `config/detekt/`,
`gradle/app-settings.gradle.kts`, `.githooks/`, `scripts/`, `Makefile`, `fastlane/`. It never writes the
application's own build files. Those need what follows; the kit's own `fixture/` is a complete, working
example of every piece. Below, `<alias>` is the `--alias` value and `io.thernal.buildkit` is already the
application's package in an installed copy of this file.

## settings.gradle.kts

```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "my-app"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
apply(from = "gradle/app-settings.gradle.kts")   // includeBuild("build-logic"), module discovery, create's arguments
```

Remove any `include(...)` list and any other `includeBuild("build-logic")`: modules are discovered from
`app.modules.areas`, and build-logic must be a top-level included build (it supplies the Detekt rules as a
dependency — `pluginManagement { includeBuild }` alone cannot).

## build.gradle.kts (root)

```kotlin
plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.dependency.analysis) apply false   // optional: enables buildHealth
    alias(libs.plugins.<alias>.modules)
    alias(libs.plugins.<alias>.detekt)
}

// build-logic's tests are not reached by `./gradlew test` on their own.
val buildLogicTest = tasks.register("buildLogicTest") {
    dependsOn(gradle.includedBuild("build-logic").task(":convention:test"))
    dependsOn(gradle.includedBuild("build-logic").task(":detekt-rules:test"))
}
tasks.register("test") { dependsOn(buildLogicTest) }
```

## gradle/libs.versions.toml

Versions: `kotlin`, `jvm` (17), `agp`, `android-compile-sdk`, `android-target-sdk`, `android-min-sdk`,
`compose-multiplatform`, `kotlinx-coroutines`, `metro`, `detekt`, `dependency-analysis`, `junit4`.

```toml
[libraries]
android-gradle-plugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
compose-gradle-plugin = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "compose-multiplatform" }
compose-compiler-gradle-plugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
dependency-analysis-gradle-plugin = { module = "com.autonomousapps:dependency-analysis-gradle-plugin", version.ref = "dependency-analysis" }
detekt-gradle-plugin = { module = "dev.detekt:detekt-gradle-plugin", version.ref = "detekt" }
detekt-api = { module = "dev.detekt:detekt-api", version.ref = "detekt" }
detekt-test = { module = "dev.detekt:detekt-test", version.ref = "detekt" }
detekt-ktlint-wrapper = { module = "dev.detekt:detekt-rules-ktlint-wrapper", version.ref = "detekt" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "compose-multiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose-multiplatform" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "compose-multiplatform" }
metro-runtime = { module = "dev.zacsweers.metro:runtime", version.ref = "metro" }
junit4 = { module = "junit:junit", version.ref = "junit4" }

[plugins]
android-kotlin-multiplatform-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
metro = { id = "dev.zacsweers.metro", version.ref = "metro" }
dependency-analysis = { id = "com.autonomousapps.dependency-analysis", version.ref = "dependency-analysis" }
<alias>-kmp-library = { id = "io.thernal.buildkit.kmp.library" }
<alias>-compose = { id = "io.thernal.buildkit.compose" }
<alias>-injection = { id = "io.thernal.buildkit.injection" }
<alias>-kotlin-library = { id = "io.thernal.buildkit.kotlin.library" }
<alias>-android-application = { id = "io.thernal.buildkit.android.application" }
<alias>-environment = { id = "io.thernal.buildkit.environment" }
<alias>-modules = { id = "io.thernal.buildkit.modules" }
<alias>-detekt = { id = "io.thernal.buildkit.detekt" }
```

## gradle.properties

```properties
app.namespace=com.example.app                  # prefix of every namespace and generated package
app.flavors=regress,dev,beta,prod              # asked at setup: at least one, no test* names
app.flavors.production=prod
app.flavors.default=dev
app.api.allowed=                               # modules exported into the iOS framework, if any
app.modules.root=                              # "" = the repository root
app.modules.areas=apps,core,designsystem,features
```

## Other files

- `.env.<flavor>` for each flavor at the root (`KEY=value`; may be empty).
- `gradle/gradle-daemon-jvm.properties` with `toolchainVersion=21` — Metro's Gradle plugin needs a JDK 21
  daemon (copy the kit's, it provisions the JDK).
- `.gitattributes`: `report/**/*.md merge=generated-report`.
- `.gitignore`: `.misc/` and `config/signing/release/`.
- `config/signing/debug/` (`keystore.properties` + keystore) to share one debug signature — optional.
- `apps/<name>/version.properties` (`versionName=`, `<flavor>.versionCode=`) — optional, defaults apply.
- A `Gemfile` with `gem "fastlane"` if the store lanes are used.

## Check

```sh
./gradlew help                 # configures; a Git hooks path and merge driver are installed
./gradlew create profile api impl wiring && ./gradlew build
git config core.hooksPath      # .githooks
```
