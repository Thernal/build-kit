package io.thernal.buildkit.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Repository-level rules, applied once to the root project. Today: no module may declare an
 * `api(...)` dependency.
 *
 * `api` puts a dependency on every consumer's compile classpath, so an ABI change there recompiles
 * the whole downstream graph and a consumer's build file no longer says what it uses. Both cost more
 * than the duplicated declaration `implementation` asks for. Kits follow the same rule, so an
 * installed kit never trips it.
 */
class ModulesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        if (this != rootProject) throw GradleException("io.thernal.buildkit.modules must be applied to the root project.")
        subprojects.forEach { subproject -> subproject.afterEvaluate(::rejectApiDependencies) }
    }
}

/**
 * The `api` buckets of a module, in every shape Gradle and Kotlin create them: `api` and
 * `<variant>Api` for Android and Java, `<sourceSet>Api` for multiplatform (`commonMainApi`,
 * `iosMainApi`).
 */
internal fun isApiConfiguration(name: String): Boolean = name == "api" || name.endsWith("Api")

private fun rejectApiDependencies(project: Project) {
    val declared = project.configurations
        .filter { isApiConfiguration(it.name) }
        .flatMap { configuration ->
            configuration.dependencies.map { dependency ->
                val coordinate = dependency.group?.let { "$it:${dependency.name}" } ?: dependency.name
                "${configuration.name}($coordinate)"
            }
        }
        .distinct()
        .sorted()
    if (declared.isEmpty()) return

    throw GradleException(
        buildString {
            appendLine("${project.path} declares api(...) dependencies: ${declared.joinToString()}.")
            appendLine("`api(...)` is not used in this repository. Use implementation(...) and declare the")
            append("dependency in every module that uses its types.")
        },
    )
}
