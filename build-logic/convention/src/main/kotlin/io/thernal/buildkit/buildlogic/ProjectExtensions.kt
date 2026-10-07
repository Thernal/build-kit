package io.thernal.buildkit.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow {
        IllegalStateException("Missing library alias '$alias' in libs.versions.toml")
    }

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias)
        .orElseThrow { IllegalStateException("Missing version '$alias' in libs.versions.toml") }
        .requiredVersion

internal fun VersionCatalog.intVersion(alias: String): Int = version(alias).toInt()

/** The trimmed, non-empty entries of a comma-separated property value; none for null. */
internal fun String?.commaSeparated(): Sequence<String> =
    orEmpty().splitToSequence(',').map(String::trim).filter(String::isNotEmpty)

/** Gradle property naming the prefix every namespace and generated package starts with. */
internal const val NAMESPACE_PROPERTY = "app.namespace"

/** Gradle property: the directory modules live under, relative to the repository root ("" = root). */
internal const val MODULES_ROOT_PROPERTY = "app.modules.root"

/** `app.modules.root` as path segments: `fixture` → `[fixture]`, unset → `[]`. */
internal fun parseModulesRoot(value: String?): List<String> =
    value.orEmpty().splitToSequence('/', ':').map(String::trim).filter(String::isNotEmpty).toList()

/** [segments] of a project path with the modules root in front of them removed. */
internal fun List<String>.belowModulesRoot(modulesRoot: List<String>): List<String> =
    if (take(modulesRoot.size) == modulesRoot) drop(modulesRoot.size) else this

/**
 * Derives the Android namespace for a Gradle project path such as `:features:profile:impl`. The
 * modules root (`app.modules.root`) is a directory, not part of any module's identity, so it is left
 * out. Segments are split on both `:` and `-`, so a `venue-management` directory contributes two
 * package segments, matching the source layout.
 */
internal fun namespaceFor(prefix: String, projectPath: String, modulesRoot: List<String> = emptyList()): String {
    val suffix = projectPath.removePrefix(":").split(':')
        .belowModulesRoot(modulesRoot)
        .asSequence()
        .flatMap { it.split('-') }
        .filter(String::isNotBlank)
        .joinToString(".") { segment -> segment.filter { it.isLetterOrDigit() || it == '_' } }
    return listOf(prefix, suffix).filter(String::isNotBlank).joinToString(".")
}

internal fun Project.defaultNamespace(): String {
    val prefix = providers.gradleProperty(NAMESPACE_PROPERTY).orNull
        ?.takeIf(String::isNotBlank)
        ?: error("Set $NAMESPACE_PROPERTY in gradle.properties, for example $NAMESPACE_PROPERTY=com.example.app")
    return namespaceFor(prefix, path, parseModulesRoot(providers.gradleProperty(MODULES_ROOT_PROPERTY).orNull))
}
