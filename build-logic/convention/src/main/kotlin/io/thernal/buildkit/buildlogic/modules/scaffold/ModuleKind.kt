package io.thernal.buildkit.buildlogic.modules.scaffold

/** The roles a capability splits into (`api` / `impl` / `wiring`). */
internal enum class ModuleKind(
    val directoryName: String,
    private val capabilities: List<String>,
    val dependsOn: List<ModuleKind>,
) {
    API("api", emptyList(), emptyList()),
    IMPL("impl", emptyList(), listOf(API)),
    WIRING("wiring", listOf("injection"), listOf(API, IMPL)),
    ;

    /** The conventions its build file names: the platform's library convention, then capabilities. */
    fun plugins(androidOnly: Boolean): List<String> =
        listOf(if (androidOnly) "android.library" else "kmp.library") + capabilities

    companion object {
        fun from(token: String): ModuleKind = entries.firstOrNull { it.directoryName == token.lowercase() }
            ?: throw IllegalArgumentException(
                "Unknown module kind '$token'. Expected one of ${entries.joinToString { it.directoryName }}.",
            )
    }
}
