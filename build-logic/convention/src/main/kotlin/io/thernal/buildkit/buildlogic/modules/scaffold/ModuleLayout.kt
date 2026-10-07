package io.thernal.buildkit.buildlogic.modules.scaffold

import io.thernal.buildkit.buildlogic.commaSeparated
import io.thernal.buildkit.buildlogic.parseModulesRoot

/** Gradle property: the top-level directories (under the modules root) that hold modules. */
internal const val MODULES_AREAS_PROPERTY = "app.modules.areas"

internal const val DEFAULT_MODULE_AREAS = "apps,core,designsystem,features"

/** Where modules live: an optional root directory and the areas under it. */
internal data class ModuleLayout(
    val root: List<String>,
    val areas: Set<String>,
) {
    /** The area a bare name (`profile`) goes to. */
    val defaultArea: String get() = if ("features" in areas) "features" else areas.first()

    companion object {
        fun parse(root: String?, areas: String?): ModuleLayout = ModuleLayout(
            root = parseModulesRoot(root),
            areas = (areas?.takeIf(String::isNotBlank) ?: DEFAULT_MODULE_AREAS).commaSeparated().toCollection(linkedSetOf()),
        )
    }
}
