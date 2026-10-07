package io.thernal.buildkit.buildlogic.modules.graph

import java.io.File
import java.io.Serializable
import kotlin.math.abs

/**
 * Coupling metrics from Robert Martin's package principles, computed per module.
 *
 * - [afferent] (Ca): modules that depend on this one.
 * - [efferent] (Ce): modules this one depends on.
 * - [instability] (I): `Ce / (Ca + Ce)`. `0.0` is maximally stable, `1.0` maximally unstable.
 * - [abstractness] (A): share of top-level declarations that are interfaces or abstract classes.
 * - [distance] (D): `|A + I - 1|`. Distance from the main sequence, where `0.0` is balanced.
 */
data class ModuleMetrics(
    val path: String,
    val afferent: Int,
    val efferent: Int,
    val instability: Double,
    val abstractness: Double,
    val distance: Double,
    val declarations: Int,
) : Serializable {
    private companion object {
        const val serialVersionUID = 1L
    }
}

private val ABSTRACT_DECLARATION = Regex(
    """^\s*(?:public |internal |private )?(?:sealed |abstract |fun )?interface\s|""" +
        """^\s*(?:public |internal |private )?(?:sealed|abstract)\s+class\s""",
    RegexOption.MULTILINE,
)
private val CONCRETE_DECLARATION = Regex(
    """^\s*(?:public |internal |private )?(?:data |value |enum |annotation )?(?:class|object)\s""",
    RegexOption.MULTILINE,
)

internal fun moduleMetrics(
    modules: List<ModuleNode>,
    known: Set<String>,
): List<ModuleMetrics> {
    val consumers = modules.asSequence()
        .flatMap { module -> module.dependencies.asSequence().distinct().filter { it != module.path } }
        .groupingBy { it }
        .eachCount()
    return modules.map { module ->
        val efferent = module.dependencies.asSequence().filter { it in known }.distinct().count()
        val afferent = consumers[module.path] ?: 0
        val coupling = afferent + efferent
        val instability = if (coupling == 0) 0.0 else efferent.toDouble() / coupling
        val (abstract, concrete) = declarationCounts(module.sourceDirectory)
        val declarations = abstract + concrete
        val abstractness = if (declarations == 0) 0.0 else abstract.toDouble() / declarations
        ModuleMetrics(
            path = module.path,
            afferent = afferent,
            efferent = efferent,
            instability = instability,
            abstractness = abstractness,
            distance = abs(abstractness + instability - 1.0),
            declarations = declarations,
        )
    }
}

/** Abstract and concrete top-level declarations in a module's production sources. */
private fun declarationCounts(sourceDirectory: String): Pair<Int, Int> {
    val root = File(sourceDirectory)
    if (!root.isDirectory) return 0 to 0
    return root.walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" && !isTestSource(root, file) }
        .map(File::readText)
        .fold(0 to 0) { (abstract, concrete), text ->
            abstract + ABSTRACT_DECLARATION.findAll(text).count() to concrete + CONCRETE_DECLARATION.findAll(text).count()
        }
}

/** `test`, `commonTest`, `androidHostTest`, `iosTest`. */
private fun isTestSource(root: File, file: File): Boolean {
    val sourceSet = file.relativeTo(root).invariantSeparatorsPath.substringBefore('/')
    return sourceSet == "test" || sourceSet.endsWith("Test")
}
