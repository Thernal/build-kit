package io.thernal.buildkit.buildlogic.modules

import org.gradle.api.Project

/** skill-manager's record of the kits this repository took code from. */
internal const val KITS_LOCK = "kits.lock"

/**
 * The modules kits installed, read from `kits.lock`. skill-manager writes a `part <path>` line for each
 * code part it took (`part core/storage/impl`): those are exact modules, and a module of the
 * application's own that lives below one (`core/domain/wiring` under arch-kit's `core/domain`) stays
 * the application's. An older lock without `part` lines falls back to each `map module FROM TO`
 * target as a prefix (`:core:storage` for storage-kit installed with `--module :core:storage`).
 */
internal data class KitModules(
    val exact: Set<String>,
    val prefixes: Set<String>,
) {
    fun contains(projectPath: String): Boolean =
        projectPath in exact || prefixes.any { kit -> projectPath == kit || projectPath.startsWith("$kit:") }
}

internal fun kitModules(lock: String?): KitModules {
    val exact = mutableSetOf<String>()
    val prefixes = mutableSetOf<String>()
    lock.orEmpty().lineSequence().map(String::trim).split { it.startsWith("[") }.forEach { block ->
        val parts = block.filter { it.startsWith("part ") }.map { ":" + it.removePrefix("part ").trim().replace('/', ':') }
        if (parts.isNotEmpty()) exact += parts else prefixes += mappedModules(block)
    }
    return KitModules(exact = exact, prefixes = prefixes)
}

/** `map module FROM TO` targets: the modules an older lock without `part` lines installed to. */
private fun mappedModules(block: List<String>): Sequence<String> =
    block.asSequence()
        .filter { it.startsWith("map module ") }
        .mapNotNull { it.split(WHITESPACE).getOrNull(3) }
        .filter { it.startsWith(":") }

private val WHITESPACE = Regex("\\s+")

/** The lines of a lock, cut into one list per `[kit]` section. */
private fun Sequence<String>.split(isHeader: (String) -> Boolean): List<List<String>> {
    val blocks = mutableListOf<MutableList<String>>()
    forEach { line ->
        if (isHeader(line) || blocks.isEmpty()) blocks += mutableListOf<String>()
        blocks.last() += line
    }
    return blocks
}

/**
 * Whether this module is code a kit installed. Such code is not analysed here: the kit's own build
 * holds it to its rules, the application does not edit it, and `// TODO: Detekt` markers written
 * into it would be local edits that turn every kit update into a merge conflict.
 */
internal fun Project.isKitModule(): Boolean {
    val lock = providers.fileContents(rootProject.layout.projectDirectory.file(KITS_LOCK)).asText.orNull
    return kitModules(lock).contains(path)
}
