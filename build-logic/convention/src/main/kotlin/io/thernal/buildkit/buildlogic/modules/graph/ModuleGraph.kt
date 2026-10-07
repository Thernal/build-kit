package io.thernal.buildkit.buildlogic.modules.graph

/** The report's entry page; every other page links back to it. */
internal const val INDEX_PAGE = "README.md"

/**
 * Renders the module report: file name to Markdown content, one file per view plus [INDEX_PAGE]
 * linking them together.
 */
internal fun renderModuleReport(
    modules: List<ModuleNode>,
    task: String,
): Map<String, String> {
    val known = modules.map(ModuleNode::path).toSet()
    val roles = modules.associate { module -> module.path to module.role }
    val metrics = moduleMetrics(modules, known).associateBy(ModuleMetrics::path)
    val conditions = modules.associate { module ->
        module.path to Condition.of(module, metrics.getValue(module.path)) { path -> roles[path] }
    }
    val waves = buildWaves(modules, known)

    return buildMap {
        put(INDEX_PAGE, indexPage(modules, conditions, waves, task))
        put("health.md", reportPage("Health", task) { append(healthBody(modules, metrics, conditions)) })
        put("build-waves.md", reportPage("Build waves", task) { append(buildWavesBody(waves)) })
        GRAPH_VIEWS.forEach { view -> put(view.file, graphPage(view, modules, known, conditions, task)) }
        put("metrics.md", reportPage("Coupling metrics", task) { append(metricsBody(modules.mapNotNull { metrics[it.path] })) })
    }
}

/** A diagram page over the modules [include] selects. */
internal class GraphView(
    val file: String,
    val title: String,
    val blurb: String,
    val include: (ModuleNode) -> Boolean,
)

internal val GRAPH_VIEWS = listOf(
    GraphView("core.md", "Core", "Modules under `core/`, the shared capability layer.") { it.area == "core" },
    GraphView("features.md", "Features", "Modules under `features/`, grouped by owner.") { it.area == "features" },
    GraphView(
        file = "core-and-features.md",
        title = "Core and features",
        blurb = "How product features consume core capabilities.",
    ) { it.area == "core" || it.area == "features" },
    GraphView("all-modules.md", "All modules", "Every module in the build, apps included.") { true },
)

private fun graphPage(
    view: GraphView,
    modules: List<ModuleNode>,
    known: Set<String>,
    conditions: Map<String, Condition>,
    task: String,
): String = reportPage(view.title, task) {
    appendLine(view.blurb)
    appendLine()
    append(arrowLegend())
    appendLine()
    append(mermaid(modules.filter(view.include), known, conditions))
}
