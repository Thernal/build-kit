package io.thernal.buildkit.buildlogic

import java.io.File
import org.gradle.api.Project

internal fun resolveAgainst(root: File, path: String): File =
    File(path).let { if (it.isAbsolute) it else root.resolve(path) }.canonicalFile

/** The canonical files a comma-separated Gradle property lists; null when the property is unset. */
internal fun Project.fileListProperty(name: String): Sequence<File>? =
    providers.gradleProperty(name).orNull?.commaSeparated()?.map { File(it).canonicalFile }
