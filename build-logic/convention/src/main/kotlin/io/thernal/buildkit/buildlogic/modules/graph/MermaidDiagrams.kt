package io.thernal.buildkit.buildlogic.modules.graph

/*
 * Mermaid diagrams for the report pages: the dependency graph of a view, and one row per build wave.
 */

internal fun mermaid(
    modules: List<ModuleNode>,
    known: Set<String>,
    conditions: Map<String, Condition>,
): String {
    if (modules.isEmpty()) return "_No modules in this view._\n"
    return buildString {
        appendLine("```mermaid")
        appendLine("graph LR")
        appendAreaSubgraphs(modules, conditions)
        appendDependencyEdges(modules, known)
        appendRoleClasses(modules)
        appendLine("```")
    }
}

private fun StringBuilder.appendAreaSubgraphs(modules: List<ModuleNode>, conditions: Map<String, Condition>) {
    modules.groupBy(ModuleNode::area).toSortedMap().forEach { (area, areaModules) ->
        appendLine("  subgraph ${mermaidId(area)}[\"$area\"]")
        areaModules.sortedBy(ModuleNode::path).forEach { module ->
            val marker = conditions[module.path]
                ?.takeIf { condition -> condition.severity != Severity.HEALTHY }
                ?.let { condition -> "${condition.severity.marker} " }
                .orEmpty()
            appendLine("    ${mermaidId(module.path)}[\"$marker${label(module.path)}\"]")
        }
        appendLine("  end")
    }
}

private fun StringBuilder.appendDependencyEdges(modules: List<ModuleNode>, known: Set<String>) {
    val visible = modules.map(ModuleNode::path).toSet()
    modules.sortedBy(ModuleNode::path).forEach { module ->
        module.dependencies.filter { it in visible && it in known }.sorted().forEach { target ->
            appendLine("  ${mermaidId(module.path)} --> ${mermaidId(target)}")
        }
    }
}

private fun StringBuilder.appendRoleClasses(modules: List<ModuleNode>) {
    appendLine("  classDef api fill:#e0eef2,stroke:#0d5c70,color:#0d5c70;")
    appendLine("  classDef impl fill:#f6ebd6,stroke:#8a6014,color:#8a6014;")
    appendLine("  classDef wiring fill:#e2efe8,stroke:#2c6a50,color:#2c6a50;")
    appendLine("  classDef app fill:#eae8f0,stroke:#5a5570,color:#5a5570;")
    listOf("api", "impl", "wiring").forEach { role ->
        appendClass(modules.filter { it.role == role }, role)
    }
    appendClass(modules.filter { it.area == "apps" }, "app")
}

private fun StringBuilder.appendClass(members: List<ModuleNode>, className: String) {
    if (members.isNotEmpty()) appendLine("  class ${members.joinToString(",") { mermaidId(it.path) }} $className;")
}

/**
 * One diagram per wave, so the waves stack simply by being separate blocks.
 *
 * Inside a wave the modules are chained with Mermaid's invisible link. They have no real
 * dependency on each other, but a link is what gives them separate ranks, and in `LR` separate
 * ranks run left to right — the row that shows the wave building at once. Without it they share
 * a rank and render as a column, which reads like a sequence they are not.
 */
internal fun waveRow(wave: List<String>): String {
    val sorted = wave.sorted()
    if (sorted.isEmpty()) return "_Empty wave._\n"

    return buildString {
        appendLine("```mermaid")
        appendLine("graph LR")
        sorted.forEach { path ->
            appendLine("  ${mermaidId(path)}[\"${path.removePrefix(":")}\"]")
        }
        sorted.zipWithNext().forEach { (left, right) ->
            appendLine("  ${mermaidId(left)} ~~~ ${mermaidId(right)}")
        }
        appendLine("```")
    }
}

/** `:core:network:api` -> `core_network_api`, which Mermaid accepts as a node id. */
internal fun mermaidId(value: String): String = value
    .removePrefix(":")
    .map { character -> if (character.isLetterOrDigit()) character else '_' }
    .joinToString(separator = "")

/** Drops the area prefix, which the surrounding subgraph already shows. */
private fun label(path: String): String {
    val withoutArea = path.removePrefix(":")
    return withoutArea.substringAfter(':', missingDelimiterValue = withoutArea)
}
