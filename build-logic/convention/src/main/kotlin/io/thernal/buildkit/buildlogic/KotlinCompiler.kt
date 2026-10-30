package io.thernal.buildkit.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/** Gradle property: the opt-in annotations every Kotlin compilation accepts, comma-separated. */
internal const val KOTLIN_OPT_IN_PROPERTY = "app.kotlin.optIn"

/**
 * The opt-ins an application accepts everywhere (`kotlin.time.ExperimentalTime`), read from
 * `gradle.properties` so no module and no file repeats them, and the unused return value checker.
 */
internal fun Project.configureKotlinCompiler() {
    val optIns = providers.gradleProperty(KOTLIN_OPT_IN_PROPERTY).orNull.commaSeparated().toList()
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.optIn.addAll(optIns)
        // Off by default in 2.4; `check` warns on a dropped value of a `@MustUseReturnValues`
        // declaration and of most of the standard library (conventions-kit rule kotlin-features 1).
        compilerOptions.freeCompilerArgs.add("-Xreturn-value-checker=check")
    }
}
