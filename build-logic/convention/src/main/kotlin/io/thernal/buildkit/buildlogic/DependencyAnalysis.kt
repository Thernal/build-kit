package io.thernal.buildkit.buildlogic

import com.autonomousapps.DependencyAnalysisExtension
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.kotlin.dsl.configure

private const val DEPENDENCY_ANALYSIS_PLUGIN = "com.autonomousapps.dependency-analysis"

/**
 * Applies the dependency-analysis plugin to every module when the root build declares it
 * (`alias(libs.plugins.dependency.analysis) apply false`), and tunes it to this build's rules. Its
 * `buildHealth` report is advice, never a failure:
 *
 * - "declare it as `api`" is ignored — `api(...)` is not used;
 * - "declare this transitive dependency directly" is ignored — Compose Multiplatform, Ktor and
 *   AndroidX pull internal artifacts no catalog names;
 * - unused dependencies are warnings, minus the known false positives: `wiring` modules (Metro reads
 *   their binding containers off the classpath, so no type of theirs is referenced) and the Compose
 *   Multiplatform artifacts, which resolve to AndroidX ones on Android and look unused there.
 */
internal fun Project.configureDependencyAnalysis() {
    try {
        pluginManager.apply(DEPENDENCY_ANALYSIS_PLUGIN)
    } catch (_: UnknownPluginException) {
        return
    }
    // The plugin applies itself only to statically declared subprojects; modules here are
    // discovered from the directory tree, so each is opted in.
    subprojects.forEach { it.pluginManager.apply(DEPENDENCY_ANALYSIS_PLUGIN) }

    val wiring = subprojects.map(Project::getPath).filter { it.endsWith(":wiring") }
    extensions.configure<DependencyAnalysisExtension> {
        issues {
            all {
                onIncorrectConfiguration { severity("ignore") }
                onUsedTransitiveDependencies { severity("ignore") }
                onUnusedDependencies {
                    severity("warn")
                    exclude(*(wiring + COMPOSE_MULTIPLATFORM_ARTIFACTS).toTypedArray())
                }
            }
        }
    }
}

private val COMPOSE_MULTIPLATFORM_ARTIFACTS = listOf(
    "org.jetbrains.compose.runtime:runtime",
    "org.jetbrains.compose.foundation:foundation",
    "org.jetbrains.compose.ui:ui",
)
