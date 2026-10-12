package io.thernal.buildkit.buildlogic.quality

/** The project's Detekt configuration; its `analysis` section is read by the build, not by Detekt. */
internal const val DETEKT_CONFIG_FILE = "config/detekt/detekt.yml"

/** Comma-separated absolute paths; set by the pre-commit hook to analyse only what changed. */
internal const val DETEKT_CHANGED_FILES_PROPERTY = "detektChangedFiles"

/** Comma-separated absolute paths of deleted or renamed-away files, whose entries are dropped. */
internal const val DETEKT_STALE_FILES_PROPERTY = "detektStaleReportFiles"

/** `true` lets ktlint rewrite formatting in place during analysis (the pre-commit hook sets it). */
internal const val DETEKT_AUTO_CORRECT_PROPERTY = "detektAutoCorrect"

/** Per module: every Detekt task whose findings count, and nothing twice. */
internal const val DETEKT_ANALYSIS_TASK = "detektAnalysis"

/** Where the counted tasks write their checkstyle XML, for the root pipeline to merge. */
internal const val DETEKT_ANALYSIS_REPORTS = "reports/detekt/analysis"

/**
 * A multiplatform module: the type-resolved Android compilations (`detektMainAndroid`,
 * `detektHostTestAndroid`), which cover common and Android sources, plus the source-set tasks for
 * everything no Android compilation sees (`detektIosMainSourceSet`, `detektAppleMainSourceSet`, …).
 */
internal fun isCountedMultiplatformAnalysis(name: String): Boolean {
    if (name == "detekt") return false
    if (!name.endsWith("SourceSet")) return true
    val sourceSet = name.removePrefix("detekt").removeSuffix("SourceSet")
    return !sourceSet.startsWith("Common") && !sourceSet.startsWith("Android")
}

/** A JVM module: the type-resolved compilations (`detektMain`, `detektTest`), which cover everything. */
internal fun isCountedJvmAnalysis(name: String): Boolean = name != "detekt" && !name.endsWith("SourceSet")

/**
 * The entries of `analysis: disabledDiagnostics:` in the Detekt configuration — compiler diagnostics Detekt's own
 * analysis must not report. Detekt's YAML has no place for compiler flags, so the section is the build's own (the
 * file's `config.excludes` keeps Detekt's validation from rejecting it). A minimal reader: a list of bare names.
 */
internal fun disabledDiagnosticsIn(yaml: String): List<String> {
    val lines = yaml.lines()
    val section = lines.indexOfFirst { it.trimEnd() == "analysis:" }
    if (section < 0) return emptyList()
    val key = lines.drop(section + 1).indexOfFirst { it.trim() == "disabledDiagnostics:" }
    if (key < 0) return emptyList()

    return lines.drop(section + 2 + key)
        .map { it.substringBefore("#").trimEnd() }
        .filter { it.isNotBlank() }
        .takeWhile { it.startsWith(" ") }
        .mapNotNull { Regex("^\\s*-\\s*(\\w+)$").find(it)?.groupValues?.get(1) }
}
