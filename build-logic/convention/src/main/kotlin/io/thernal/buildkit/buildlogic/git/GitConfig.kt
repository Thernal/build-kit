package io.thernal.buildkit.buildlogic.git

import io.thernal.buildkit.buildlogic.resolveAgainst
import java.io.File

/*
 * Repository Git settings the build keeps in place, written straight into Git's config file — no
 * `git` process, so it is safe during configuration and works from a linked worktree.
 */

/** The merge driver `.gitattributes` assigns to the generated reports under `report/`. */
internal const val REPORT_MERGE_DRIVER = "generated-report"

/**
 * Points `core.hooksPath` at `.githooks`, so the pre-commit and post-merge hooks run after the first
 * Gradle sync. Skipped on CI, where automated commits record unresolved findings on purpose and must
 * not be blocked.
 */
internal fun installGitHooks(rootDirectory: File, isCi: Boolean) {
    if (isCi || !rootDirectory.resolve(".githooks").isDirectory) return
    val config = gitConfigFile(rootDirectory) ?: return
    config.writeText(withHooksPath(config.readText()) ?: return)
}

/**
 * Declares the driver generated reports merge with: a conflict in one is never a real disagreement,
 * so it keeps the checked-out side (`driver = true`) and the post-merge hook regenerates the report
 * from the merged build files. The command has to live in Git config; `.gitattributes` can only
 * name it.
 */
internal fun installReportMergeDriver(rootDirectory: File) {
    val config = gitConfigFile(rootDirectory) ?: return
    config.writeText(withReportMergeDriver(config.readText()) ?: return)
}

/**
 * Makes the hooks and scripts executable again. A copy that goes through a text transformation — a kit
 * install renaming the package — writes new files without the executable bit, and Git silently skips
 * a hook that is not executable: the pre-commit check would simply never run.
 */
internal fun restoreExecutableBits(rootDirectory: File) {
    val hooks = rootDirectory.resolve(".githooks").listFiles().orEmpty().filter(File::isFile)
    val scripts = rootDirectory.resolve("scripts").listFiles().orEmpty().filter { it.isFile && it.extension == "sh" }
    (hooks + scripts).filterNot(File::canExecute).forEach { it.setExecutable(true, false) }
}

/** [content] with `core.hooksPath = .githooks`, or null when it already says so. */
internal fun withHooksPath(content: String): String? {
    val pattern = Regex("""(?m)^(\s*hooksPath\s*=\s*)[^\r\n]*""", RegexOption.IGNORE_CASE)
    val current = pattern.find(content)?.value?.substringAfter('=')?.trim()
    return when {
        current == ".githooks" -> null
        current != null -> content.replace(pattern, "$1.githooks")
        else -> content.withSection("[core]\n\thooksPath = .githooks\n")
    }
}

/** [content] with the report merge driver declared, or null when it already is. */
internal fun withReportMergeDriver(content: String): String? {
    if (content.contains("[merge \"$REPORT_MERGE_DRIVER\"]")) return null
    return content.withSection(
        "[merge \"$REPORT_MERGE_DRIVER\"]\n\tname = generated report, regenerated after merge\n\tdriver = true\n",
    )
}

private fun String.withSection(section: String): String =
    this + (if (isEmpty() || endsWith('\n')) "" else "\n") + section

private fun gitConfigFile(rootDirectory: File): File? {
    val marker = rootDirectory.resolve(".git")
    val gitDirectory = when {
        marker.isDirectory -> marker
        marker.isFile -> marker.readText().substringAfter("gitdir:", "").trim()
            .takeIf(String::isNotEmpty)
            ?.let { resolveAgainst(rootDirectory, it) }
        else -> null
    } ?: return null
    val commonDirectory = gitDirectory.resolve("commondir").takeIf(File::isFile)
        ?.readText()?.trim()?.takeIf(String::isNotEmpty)
        ?.let { resolveAgainst(gitDirectory, it) }
        ?: gitDirectory
    return commonDirectory.resolve("config").takeIf(File::isFile)
}
