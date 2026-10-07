package io.thernal.buildkit.buildlogic.modules.graph

/** Severity of a module's standing in the dependency graph, worst first. */
internal enum class Severity(val marker: String, val label: String) {
    PROBLEM("🔴", "Needs attention"),
    WATCH("🟡", "Worth watching"),
    HEALTHY("🟢", "Healthy"),
}

/**
 * A named condition a module can be in, with the action that resolves it.
 *
 * Conditions are ordered: the first one that matches wins, so a module carries one verdict rather
 * than a list of overlapping metrics.
 */
internal enum class Condition(
    val severity: Severity,
    val title: String,
    val meaning: String,
    val action: String,
) {
    UNUSED(
        severity = Severity.PROBLEM,
        title = "Unused",
        meaning = "No module depends on it.",
        action = "Wire it into the feature that needs it, or delete it.",
    ),
    BOUNDARY_VIOLATION(
        severity = Severity.PROBLEM,
        title = "Boundary violation",
        meaning = "An `api` module depends on an `impl` or `wiring` module, which inverts the split.",
        action = "Move the type the contract needs into an `api` module.",
    ),
    CONCRETE_HUB(
        severity = Severity.WATCH,
        title = "Concrete hub",
        meaning = "Several modules depend on it and it is mostly concrete classes, so a change ripples widely.",
        action = "Extract the contract consumers actually use into an `api` module.",
    ),
    WIDE_SURFACE(
        severity = Severity.WATCH,
        title = "Wide surface",
        meaning = "It depends on many modules, so it rebuilds often and is hard to reason about.",
        action = "Check whether it is doing more than one job and split it.",
    ),
    HEALTHY(
        severity = Severity.HEALTHY,
        title = "Healthy",
        meaning = "Balanced coupling for its role.",
        action = "Nothing to do.",
    ),
    ;

    companion object {
        private const val HUB_CONSUMERS = 3
        private const val HUB_ABSTRACTNESS = 0.3
        private const val WIDE_DEPENDENCIES = 8

        /**
         * Apps are composition roots: nothing depends on them and they depend on everything, so the
         * unused and wide-surface checks would flag them for doing their job.
         */
        fun of(
            module: ModuleNode,
            metrics: ModuleMetrics,
            roleOf: (String) -> String?,
        ): Condition {
            val isApp = module.area == "apps"
            return when {
                !isApp && metrics.afferent == 0 -> UNUSED
                module.role == "api" && module.dependencies.any { roleOf(it) in IMPLEMENTATION_ROLES } ->
                    BOUNDARY_VIOLATION
                metrics.afferent >= HUB_CONSUMERS && metrics.abstractness < HUB_ABSTRACTNESS -> CONCRETE_HUB
                !isApp && metrics.efferent >= WIDE_DEPENDENCIES -> WIDE_SURFACE
                else -> HEALTHY
            }
        }

        private val IMPLEMENTATION_ROLES = setOf("impl", "wiring")
    }
}
