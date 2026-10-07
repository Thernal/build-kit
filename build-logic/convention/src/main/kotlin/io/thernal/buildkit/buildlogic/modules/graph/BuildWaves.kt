package io.thernal.buildkit.buildlogic.modules.graph

/** Longest path from a leaf, so a module sits one wave after its deepest dependency. */
internal fun buildWaves(
    modules: List<ModuleNode>,
    known: Set<String>,
): List<List<String>> {
    val edges = modules.associate { module ->
        module.path to module.dependencies.filter { it in known }.distinct()
    }
    val depth = mutableMapOf<String, Int>()

    fun depthOf(
        path: String,
        seen: Set<String>,
    ): Int = depth.getOrPut(path) {
        if (path in seen) return@getOrPut 0
        val dependencies = edges[path].orEmpty()
        if (dependencies.isEmpty()) 0 else dependencies.maxOf { depthOf(it, seen + path) } + 1
    }

    modules.forEach { module -> depthOf(module.path, emptySet()) }
    val grouped = modules.groupBy { module -> depth.getValue(module.path) }
    return (0..(grouped.keys.maxOrNull() ?: 0)).map { level ->
        grouped[level].orEmpty().map(ModuleNode::path)
    }
}
