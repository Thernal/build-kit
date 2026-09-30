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

// At the top level, not in pluginManagement: besides the convention plugins, build-logic supplies the
// custom Detekt rules as a dependency (`io.thernal.buildkit.buildlogic:detekt-rules`), and only a
// top-level included build substitutes dependencies.
includeBuild("build-logic")

// Every directory under the given root that carries a build file is a module. Adding one is
// creating the directory — there is no list here to keep in sync with the tree.
fun includeModulesUnder(path: String) {
    val root = settingsDir.resolve(path)
    if (!root.exists()) return

    root.walkTopDown()
        .onEnter { directory -> directory.name != "build" && !directory.name.startsWith(".") }
        .filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
        .forEach { moduleDir ->
            val modulePath = moduleDir
                .relativeTo(settingsDir)
                .invariantSeparatorsPath
                .replace('/', ':')
            include(":$modulePath")
        }
}

// The fixture is a small application built only from build-kit's conventions: it is how the kit
// proves that each plugin configures and builds, and it is not copied into an application.
includeModulesUnder("fixture")
