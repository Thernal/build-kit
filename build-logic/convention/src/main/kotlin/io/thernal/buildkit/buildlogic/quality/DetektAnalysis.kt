package io.thernal.buildkit.buildlogic.quality

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
