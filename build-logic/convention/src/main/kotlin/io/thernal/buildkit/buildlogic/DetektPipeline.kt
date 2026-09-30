package io.thernal.buildkit.buildlogic

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/*
 * The file work behind the root Detekt tasks, kept free of Gradle types so it can be tested.
 *
 * The persisted report, `.misc/detekt/detekt-report.xml`, holds every finding known for every file
 * scanned so far. A scoped run replaces the entries of the files it scanned; a full run replaces the
 * whole report.
 */

internal const val DETEKT_BASELINE = ".misc/detekt/detekt-report.xml"
internal const val DETEKT_TODO_MARKER = "// TODO: Detekt"

private val DETEKT_TODO = Regex("""\s*// TODO: Detekt \[.*]\s*$""")
private val FILE_BLOCK = Regex("""<file name="([^"]+)"[^>]*>[\s\S]*?</file>""")

internal fun stripDetektTodo(line: String): String = line.replace(DETEKT_TODO, "").trimEnd()

/** Removes every marker from [content]; returns null when there was none. */
internal fun clearDetektTodos(content: String): String? {
    val cleaned = content.lineSequence()
        .map(::stripDetektTodo)
        .joinToString("\n")
        .let { if (it.isEmpty()) it else it.trimEnd() + "\n" }
    return cleaned.takeIf { it != content }
}

/**
 * `<file>` blocks of a checkstyle report, keyed by canonical path. Detekt writes paths relative to
 * the repository root; each block is rewritten to carry the absolute path it is keyed by, so the
 * persisted report means the same thing to the hook as to Gradle.
 */
internal fun fileBlocks(report: String, canonical: (String) -> String): Map<String, String> =
    FILE_BLOCK.findAll(report).associate { match ->
        val path = canonical(match.groupValues[1])
        path to match.value.replaceFirst("name=\"${match.groupValues[1]}\"", "name=\"$path\"")
    }

/**
 * The next persisted report. [fresh] are this run's blocks; [scanned] the files this run analysed
 * (null for a full run, which replaces everything); [stale] deleted or renamed-away files.
 */
internal fun mergeBaseline(
    previous: Map<String, String>,
    fresh: Map<String, String>,
    scanned: Set<String>?,
    stale: Set<String>,
): String {
    val kept = linkedMapOf<String, String>()
    if (scanned != null) {
        previous.forEach { (path, block) -> if (path !in scanned && path !in stale) kept[path] = block }
    }
    fresh.forEach { (path, block) -> if (path !in stale) kept[path] = block }
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<checkstyle version=\"4.3\">\n")
        kept.values.forEach { append(it).append('\n') }
        append("</checkstyle>\n")
    }
}

/** Findings of a checkstyle report: file → line → `Rule: message` labels; paths resolve against [root]. */
internal fun findings(report: File, root: File): Map<File, Map<Int, Set<String>>> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report)
    val result = linkedMapOf<File, MutableMap<Int, MutableSet<String>>>()
    val files = document.getElementsByTagName("file")
    for (fileIndex in 0 until files.length) {
        val fileNode = files.item(fileIndex) as Element
        val source = resolveAgainst(root, fileNode.getAttribute("name"))
        val errors = fileNode.getElementsByTagName("error")
        for (errorIndex in 0 until errors.length) {
            val error = errors.item(errorIndex) as Element
            val line = error.getAttribute("line").toIntOrNull() ?: continue
            val rule = error.getAttribute("source").substringAfterLast('.')
            val message = error.getAttribute("message").replace('\n', ' ').replace(']', ')').trim()
            result.getOrPut(source) { linkedMapOf() }
                .getOrPut(line) { linkedSetOf() }
                .add(if (message.isEmpty()) rule else "$rule: $message")
        }
    }
    return result
}

/** [content] with each finding's labels appended to its line as a marker. */
internal fun annotate(content: String, findings: Map<Int, Set<String>>): String {
    val lines = content.lines().toMutableList()
    if (lines.lastOrNull()?.isEmpty() == true) lines.removeAt(lines.lastIndex)
    findings.forEach { (lineNumber, labels) ->
        val index = lineNumber - 1
        if (index in lines.indices) {
            lines[index] = stripDetektTodo(lines[index]) + " $DETEKT_TODO_MARKER [${labels.sorted().joinToString(", ")}]"
        }
    }
    return lines.joinToString("\n") + "\n"
}

internal fun resolveAgainst(root: File, path: String): File =
    File(path).let { if (it.isAbsolute) it else root.resolve(path) }.canonicalFile
