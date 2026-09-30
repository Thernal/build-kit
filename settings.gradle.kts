@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "build-kit"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// create's arguments, build-logic, and module discovery under app.modules.root / app.modules.areas —
// the part an application copies with the kit.
apply(from = "gradle/app-settings.gradle.kts")
