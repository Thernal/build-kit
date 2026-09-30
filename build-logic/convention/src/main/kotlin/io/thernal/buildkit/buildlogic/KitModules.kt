package io.thernal.buildkit.buildlogic

import org.gradle.api.Project

/** skill-manager's record of the kits this repository took code from. */
internal const val KITS_LOCK = "kits.lock"

/**
 * The Gradle paths a kit's code was installed under: the target of each `map module FROM TO` line in
 * `kits.lock` (`:core:storage` for storage-kit installed with `--module :core:storage`).
 */
internal fun kitModulePaths(lock: String?): Set<String> =
    lock.orEmpty().lineSequence()
        .map(String::trim)
        .filter { it.startsWith("map module ") }
        .mapNotNull { it.split(Regex("\\s+")).getOrNull(3) }
        .filter { it.startsWith(":") }
        .toSet()

internal fun isKitModule(projectPath: String, kitPaths: Set<String>): Boolean =
    kitPaths.any { kit -> projectPath == kit || projectPath.startsWith("$kit:") }

/**
 * Whether this module is code a kit installed. Such code is not analysed here: the kit's own build
 * holds it to its rules, the application does not edit it, and `// TODO: Detekt` markers written
 * into it would be local edits that turn every kit update into a merge conflict.
 */
internal fun Project.isKitModule(): Boolean {
    val lock = providers.fileContents(rootProject.layout.projectDirectory.file(KITS_LOCK)).asText.orNull
    return isKitModule(path, kitModulePaths(lock))
}
