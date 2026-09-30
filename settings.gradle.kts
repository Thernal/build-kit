@file:Suppress("UnstableApiUsage")

// `./gradlew create profile api impl` — Gradle would read `profile`, `api` and `impl` as task names.
// Everything after `create` is handed to the task as its arguments instead. This has to happen here:
// settings are evaluated before task names are resolved, and nothing later can take them back.
run {
    val taskNames = startParameter.taskNames
    val createIndex = taskNames.indexOfFirst { it == "create" || it == ":create" }
    if (createIndex < 0) return@run
    startParameter.setTaskNames(taskNames.take(createIndex + 1))
    System.setProperty("app.create.arguments", taskNames.drop(createIndex + 1).joinToString(" "))
}

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
