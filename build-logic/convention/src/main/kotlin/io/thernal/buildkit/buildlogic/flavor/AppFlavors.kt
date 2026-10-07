package io.thernal.buildkit.buildlogic.flavor

import io.thernal.buildkit.buildlogic.commaSeparated
import org.gradle.api.GradleException
import org.gradle.api.Project

internal const val FLAVORS_PROPERTY = "app.flavors"
internal const val PRODUCTION_FLAVOR_PROPERTY = "app.flavors.production"
internal const val DEFAULT_FLAVOR_PROPERTY = "app.flavors.default"

/** The Android product flavor dimension every application module declares. */
internal const val ENVIRONMENT_DIMENSION = "environment"

private val FLAVOR_NAME = Regex("[a-z][a-z0-9]*")
private const val MINIMUM_FLAVORS = 1

/**
 * The environment flavors an application builds, read from `gradle.properties`:
 * `app.flavors` (ordered, at least one), `app.flavors.production` and `app.flavors.default`. A single
 * flavor is both production and default: an app with one environment still gets the flavor dimension,
 * so variant names, signing and the scripts stay the same as with several.
 *
 * The list is data, not code, so adding a flavor is a property edit plus its `.env.<flavor>` file —
 * no convention changes.
 */
internal data class AppFlavors(
    val all: List<String>,
    val production: String,
    val default: String,
) {
    val nonProduction: List<String> get() = all - production

    fun require(name: String, source: String): String {
        val normalized = name.trim().lowercase()
        if (normalized !in all) {
            throw GradleException("Unknown flavor '$name' in $source. Expected one of: ${all.joinToString()}")
        }
        return normalized
    }

    companion object {
        fun parse(flavors: String?, production: String?, default: String?): AppFlavors {
            val all = flavors.commaSeparated().toList()
            validateList(all, flavors)
            val productionFlavor = production.orIfBlank(all.last())
            val defaultFlavor = default.orIfBlank(all.first())
            requireListed(PRODUCTION_FLAVOR_PROPERTY, productionFlavor, all)
            requireListed(DEFAULT_FLAVOR_PROPERTY, defaultFlavor, all)
            return AppFlavors(all, productionFlavor, defaultFlavor)
        }

        private fun validateList(all: List<String>, raw: String?) {
            if (all.size < MINIMUM_FLAVORS) {
                throw GradleException("$FLAVORS_PROPERTY needs at least $MINIMUM_FLAVORS flavor, found '${raw.orEmpty()}'.")
            }
            all.forEach(::validateName)
            if (all.toSet().size != all.size) {
                throw GradleException("$FLAVORS_PROPERTY lists a flavor twice: ${all.joinToString()}")
            }
        }

        private fun String?.orIfBlank(fallback: String): String = this?.trim()?.takeIf(String::isNotEmpty) ?: fallback

        private fun requireListed(property: String, value: String, all: List<String>) {
            if (value !in all) {
                throw GradleException("$property='$value' is not in $FLAVORS_PROPERTY (${all.joinToString()}).")
            }
        }

        // Android rejects flavor names that collide with its own source sets and task prefixes, and
        // a flavor is also a Kotlin identifier segment (`regressImplementation`, `RegressDebug`).
        private fun validateName(name: String) {
            if (!name.matches(FLAVOR_NAME)) {
                throw GradleException("Flavor '$name' must be lowercase letters and digits, starting with a letter.")
            }
            if (name.startsWith("test") || name.startsWith("androidtest") || name in RESERVED_NAMES) {
                throw GradleException(
                    "Flavor '$name' collides with an Android source set or build type name; choose another " +
                        "(autonomous test builds use 'regress').",
                )
            }
        }

        private val RESERVED_NAMES = setOf("main", "debug", "release", "lint", "androidtest")
    }
}

/**
 * The application id suffix a non-production flavor gets, without the dot: the flavor's name, unless
 * `app.flavors.<flavor>.idSuffix` names another — two flavors sharing one installed identity (a
 * staging build on the dev app's Firebase client).
 */
internal fun Project.applicationIdSuffix(flavor: String): String =
    applicationIdSuffix(flavor, providers.gradleProperty("$FLAVORS_PROPERTY.$flavor.idSuffix").orNull)

internal fun applicationIdSuffix(flavor: String, override: String?): String =
    override?.trim()?.removePrefix(".")?.takeIf(String::isNotEmpty) ?: flavor

internal fun Project.appFlavors(): AppFlavors = AppFlavors.parse(
    flavors = providers.gradleProperty(FLAVORS_PROPERTY).orNull,
    production = providers.gradleProperty(PRODUCTION_FLAVOR_PROPERTY).orNull,
    default = providers.gradleProperty(DEFAULT_FLAVOR_PROPERTY).orNull,
)
