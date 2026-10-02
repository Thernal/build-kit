package io.thernal.buildkit.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project

internal const val FLAVORS_PROPERTY = "app.flavors"
internal const val PRODUCTION_FLAVOR_PROPERTY = "app.flavors.production"
internal const val DEFAULT_FLAVOR_PROPERTY = "app.flavors.default"
internal const val ACTIVE_FLAVOR_PROPERTY = "app.env"

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
            val all = flavors.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            if (all.size < MINIMUM_FLAVORS) {
                throw GradleException(
                    "$FLAVORS_PROPERTY needs at least $MINIMUM_FLAVORS flavor, found '${flavors.orEmpty()}'.",
                )
            }
            all.forEach(::validateName)
            if (all.toSet().size != all.size) {
                throw GradleException("$FLAVORS_PROPERTY lists a flavor twice: ${all.joinToString()}")
            }
            val productionFlavor = production?.trim()?.takeIf(String::isNotEmpty) ?: all.last()
            val defaultFlavor = default?.trim()?.takeIf(String::isNotEmpty) ?: all.first()
            listOf(
                PRODUCTION_FLAVOR_PROPERTY to productionFlavor,
                DEFAULT_FLAVOR_PROPERTY to defaultFlavor,
            ).forEach { (property, value) ->
                if (value !in all) {
                    throw GradleException("$property='$value' is not in $FLAVORS_PROPERTY (${all.joinToString()}).")
                }
            }
            return AppFlavors(all, productionFlavor, defaultFlavor)
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

/**
 * The one flavor this Gradle invocation builds for.
 *
 * Kotlin Multiplatform library modules have no product flavors, so the flavor a shared module is
 * compiled for — its generated `Environment`, its flavor-scoped dependencies — is decided once per
 * invocation, in this order:
 *
 * 1. `-Papp.env=<flavor>`;
 * 2. the flavor in the requested task names (`assembleBetaDebug`, `bundleProd`), which is how an
 *    IDE build of one variant gets the right flavor without passing a property;
 * 3. Xcode's `CONFIGURATION` (`beta Debug` → `beta`), for the iOS framework build phase;
 * 4. `app.env=<flavor>` in `local.properties`, a developer's standing choice;
 * 5. `app.flavors.default`.
 *
 * An explicit flavor that contradicts the requested tasks, or tasks spanning two flavors, fail: one
 * invocation cannot compile shared code for two environments.
 */
fun Project.activeFlavor(): String {
    val flavors = appFlavors()
    val fromTasks = flavorFromTaskNames(gradle.startParameter.taskNames, flavors)
    val explicit = providers.gradleProperty(ACTIVE_FLAVOR_PROPERTY).orNull
        ?.takeIf(String::isNotBlank)
        ?.let { flavors.require(it, "-P$ACTIVE_FLAVOR_PROPERTY") }

    if (explicit != null && fromTasks != null && explicit != fromTasks) {
        throw GradleException(
            "$ACTIVE_FLAVOR_PROPERTY='$explicit' contradicts the requested $fromTasks variant tasks. " +
                "Align -P$ACTIVE_FLAVOR_PROPERTY with the variant being built.",
        )
    }
    return explicit
        ?: fromTasks
        ?: flavorFromXcodeConfiguration(flavors)
        ?: flavorFromLocalProperties(flavors)
        ?: flavors.default
}

/**
 * The flavor named by Android variant task names, or null when none names one. Variant tasks carry
 * it as a capitalized segment before the build type (`assembleBetaDebug`, `testProdReleaseUnitTest`)
 * or at the end of an aggregate (`assembleBeta`, `bundleProd`, `installDev`).
 */
internal fun flavorFromTaskNames(taskNames: List<String>, flavors: AppFlavors): String? {
    val alternatives = flavors.all.joinToString("|") { it.replaceFirstChar(Char::uppercaseChar) }
    val variant = Regex("($alternatives)(Debug|Release)")
    val aggregate = Regex("^(?:assemble|bundle|install)($alternatives)$")

    val found = taskNames
        .map { it.substringAfterLast(':') }
        .mapNotNull { name ->
            (variant.find(name) ?: aggregate.find(name))?.groupValues?.get(1)?.lowercase()
        }
        .toSet()
    if (found.size > 1) {
        throw GradleException(
            "Requested tasks span several flavors (${found.joinToString()}). Build one flavor per Gradle " +
                "invocation: shared code is compiled for a single environment.",
        )
    }
    return found.singleOrNull()
}

private fun Project.flavorFromXcodeConfiguration(flavors: AppFlavors): String? =
    providers.environmentVariable("CONFIGURATION").orNull
        ?.substringBefore(' ')
        ?.lowercase()
        ?.takeIf { it in flavors.all }

private fun Project.flavorFromLocalProperties(flavors: AppFlavors): String? {
    val content = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull ?: return null
    val value = content.lineSequence()
        .map(String::trim)
        .firstOrNull { it.startsWith("$ACTIVE_FLAVOR_PROPERTY=") }
        ?.substringAfter('=')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return flavors.require(value, "local.properties")
}
