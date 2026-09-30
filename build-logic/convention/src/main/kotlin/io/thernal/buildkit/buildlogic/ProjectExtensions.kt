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

/** Gradle property naming the prefix every namespace and generated package starts with. */
internal const val NAMESPACE_PROPERTY = "app.namespace"

/**
 * Derives the Android namespace for a Gradle project path such as `:features:profile:impl`. Segments
 * are split on both `:` and `-`, so a `venue-management` directory contributes two package segments,
 * matching the source layout.
 */
internal fun namespaceFor(prefix: String, projectPath: String): String {
    val suffix = projectPath
        .removePrefix(":")
        .split(':', '-')
        .filter(String::isNotBlank)
        .joinToString(".") { segment -> segment.filter { it.isLetterOrDigit() || it == '_' } }
    return listOf(prefix, suffix).filter(String::isNotBlank).joinToString(".")
}

internal fun Project.defaultNamespace(): String {
    val prefix = providers.gradleProperty(NAMESPACE_PROPERTY).orNull
        ?.takeIf(String::isNotBlank)
        ?: error("Set $NAMESPACE_PROPERTY in gradle.properties, for example $NAMESPACE_PROPERTY=com.example.app")
    return namespaceFor(prefix, path)
}
