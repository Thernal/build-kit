// Plugins are declared here without applying them so every subproject's classloader shares one
// instance rather than loading its own copy; build-logic's conventions apply them by id.
plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.dependency.analysis) apply false
    alias(libs.plugins.buildkit.modules)
    alias(libs.plugins.buildkit.detekt)
}

// build-logic is an included build, so its tests are not reached by the main build's `test` on
// their own. An unqualified `./gradlew test` runs the task in every project that has one, so
// registering it on the root is enough.
val buildLogicTest = tasks.register("buildLogicTest") {
    group = "verification"
    description = "Runs the tests of build-logic's conventions and custom Detekt rules."
    dependsOn(gradle.includedBuild("build-logic").task(":convention:test"))
    dependsOn(gradle.includedBuild("build-logic").task(":detekt-rules:test"))
}

tasks.register("test") {
    group = "verification"
    description = "Runs build-logic's tests alongside the module tests."
    dependsOn(buildLogicTest)
}

// `build` on the root has no project of its own to build; make it run build-logic's checks too.
tasks.register("build") {
    group = "build"
    dependsOn(buildLogicTest)
}
