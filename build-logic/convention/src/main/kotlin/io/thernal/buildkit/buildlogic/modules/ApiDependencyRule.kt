package io.thernal.buildkit.buildlogic.modules

import io.thernal.buildkit.buildlogic.commaSeparated
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency

/** Gradle property listing the dependencies `api(...)` may still name, comma-separated. */
internal const val API_ALLOWED_PROPERTY = "app.api.allowed"

/*
 * No module may declare an `api(...)` dependency.
 *
 * `api` puts a dependency on every consumer's compile classpath, so an ABI change there recompiles
 * the whole downstream graph and a consumer's build file no longer says what it uses. Both cost more
 * than the duplicated declaration `implementation` asks for. Kits follow the same rule, so an
 * installed kit never trips it.
 *
 * The one thing only `api` can do is Kotlin/Native `export(...)`: a module exported into the iOS
 * framework, so Swift sees its types (Firebase's Swift bridges), has to be an `api` dependency of
 * the framework module. Such dependencies are listed by name in `app.api.allowed` — a project path
 * (`:core:firebase`) or a `group:name` coordinate — and nothing else passes.
 */

internal fun parseAllowedApi(value: String?): Set<String> = value.commaSeparated().toSet()

/**
 * The `api` buckets of a module, in every shape Gradle and Kotlin create them: `api` and
 * `<variant>Api` for Android and Java, `<sourceSet>Api` for multiplatform (`commonMainApi`,
 * `iosMainApi`).
 */
internal fun isApiConfiguration(name: String): Boolean = name == "api" || name.endsWith("Api")

internal fun rejectApiDependencies(project: Project, allowed: Set<String>) {
    val declared = project.configurations.asSequence()
        .filter { isApiConfiguration(it.name) }
        .flatMap { configuration ->
            configuration.dependencies.asSequence()
                .map(::notation)
                .filterNot { it in allowed }
                .map { "${configuration.name}($it)" }
        }
        .distinct()
        .sorted()
        .toList()
    if (declared.isEmpty()) return

    throw GradleException(
        buildString {
            appendLine("${project.path} declares api(...) dependencies: ${declared.joinToString()}.")
            appendLine("`api(...)` is not used in this repository. Use implementation(...) and declare the")
            appendLine("dependency in every module that uses its types. A module exported into the iOS framework")
            append("is the one exception: add it to $API_ALLOWED_PROPERTY in gradle.properties.")
        },
    )
}

private fun notation(dependency: Dependency): String = when (dependency) {
    is ProjectDependency -> dependency.path
    else -> dependency.group?.let { "$it:${dependency.name}" } ?: dependency.name
}
