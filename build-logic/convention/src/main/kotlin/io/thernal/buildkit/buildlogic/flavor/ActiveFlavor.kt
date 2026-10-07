package io.thernal.buildkit.buildlogic.flavor

import org.gradle.api.GradleException
import org.gradle.api.Project

internal const val ACTIVE_FLAVOR_PROPERTY = "app.env"

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

    val found = taskNames.asSequence()
        .map { it.substringAfterLast(':') }
        .mapNotNull { name -> (variant.find(name) ?: aggregate.find(name))?.groupValues?.get(1)?.lowercase() }
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
