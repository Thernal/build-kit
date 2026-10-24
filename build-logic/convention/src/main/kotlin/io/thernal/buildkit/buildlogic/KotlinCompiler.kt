package io.thernal.buildkit.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/** Gradle property: the opt-in annotations every Kotlin compilation accepts, comma-separated. */
internal const val KOTLIN_OPT_IN_PROPERTY = "app.kotlin.optIn"

/**
 * The opt-ins an application accepts everywhere (`kotlin.time.ExperimentalTime`), read from
 * `gradle.properties` so no module and no file repeats them.
 */
internal fun Project.configureKotlinCompiler() {
    val optIns = providers.gradleProperty(KOTLIN_OPT_IN_PROPERTY).orNull.commaSeparated().toList()
    if (optIns.isEmpty()) return
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.optIn.addAll(optIns)
    }
}
