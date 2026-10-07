package io.thernal.buildkit.buildlogic.compose.stability

import java.io.File

internal val SUMMARY_FIELDS = listOf(
    "totalComposables", "skippableComposables", "restartableComposables", "totalClasses", "effectivelyStableClasses",
)

internal data class ModuleStability(
    val path: String,
    val fields: Map<String, Int>,
    val unstableClasses: List<String>,
    val nonSkippableComposables: List<String>,
)

/** A multiplatform module compiles Compose once per target; the Android compilation stands in. */
internal fun preferredMetricsFile(metricsDirectory: File): File? =
    metricsDirectory.walkTopDown()
        .filter { it.isFile && it.name.endsWith("-module.json") }
        .sortedWith(compareBy({ !it.invariantSeparatorsPath.contains("/android/") }, { it.invariantSeparatorsPath }))
        .firstOrNull()

internal fun parseModuleMetrics(text: String): Map<String, Int> =
    Regex(""""(\w+)"\s*:\s*(-?\d+)""").findAll(text).associate { it.groupValues[1] to it.groupValues[2].toInt() }

private fun reportLines(reportsDirectory: File, suffix: String): Sequence<String> =
    reportsDirectory.walkTopDown().filter { it.isFile && it.name.endsWith(suffix) }.sortedBy(File::getName)
        .flatMap { it.readLines().asSequence() }

internal fun unstableClasses(reportsDirectory: File): List<String> =
    reportLines(reportsDirectory, "-classes.txt")
        .filter { it.startsWith("unstable class ") }
        .map { it.removePrefix("unstable class ").substringBefore(" {").trim() }
        .distinct().toList()

/** Restartable composables the compiler could not make skippable — the ones that recompose needlessly. */
internal fun nonSkippableComposables(reportsDirectory: File): List<String> =
    reportLines(reportsDirectory, "-composables.txt")
        .filter { it.startsWith("restartable ") && !it.split(' ').contains("skippable") }
        .map { it.substringAfter(" fun ").substringBefore('(').trim() }
        .distinct().toList()
