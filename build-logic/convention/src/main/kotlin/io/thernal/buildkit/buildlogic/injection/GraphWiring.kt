package io.thernal.buildkit.buildlogic.injection

import io.thernal.buildkit.buildlogic.belowModulesRoot

/** Gradle property: module paths every app's graph leaves out, comma-separated. */
internal const val GRAPH_EXCLUDE_PROPERTY = "app.graph.exclude"

/** Gradle property: module paths [app]'s graph leaves out, on top of [GRAPH_EXCLUDE_PROPERTY]. */
internal fun graphExcludeProperty(app: String): String = "app.graph.$app.exclude"

/**
 * The wiring modules an app's graph takes: every `…:wiring` project outside the `apps` area, minus [excluded].
 * An excluded path takes itself and everything below it out — `:core:firebase` removes
 * `:core:firebase:crash:wiring`, not `:core:firebasex:wiring`.
 */
internal fun graphWiring(
    projectPaths: Collection<String>,
    modulesRoot: List<String>,
    excluded: Collection<String>,
): List<String> {
    val prefixes = excluded.map { path -> path.trimEnd(':') }
    return projectPaths
        .filter { path -> path.endsWith(":wiring") }
        .filterNot { path -> path.segments().belowModulesRoot(modulesRoot).firstOrNull() == APPS_AREA }
        .filterNot { path -> prefixes.any { prefix -> path == prefix || path.startsWith("$prefix:") } }
        .sorted()
}

/** The entries of [excluded] that name no project and no directory of projects — a typo would hide nothing. */
internal fun unknownGraphExclusions(projectPaths: Collection<String>, excluded: Collection<String>): List<String> =
    excluded.filterNot { entry ->
        val prefix = entry.trimEnd(':')
        projectPaths.any { path -> path == prefix || path.startsWith("$prefix:") }
    }

/** The app a module belongs to: `:apps:terz:shared` → `terz`, `:fixture:apps:kiosk` → `kiosk`; null outside `apps`. */
internal fun appNameOf(projectPath: String, modulesRoot: List<String>): String? {
    val segments = projectPath.segments().belowModulesRoot(modulesRoot)
    return segments.getOrNull(1).takeIf { segments.firstOrNull() == APPS_AREA }
}

private fun String.segments(): List<String> = removePrefix(":").split(':')

private const val APPS_AREA = "apps"
