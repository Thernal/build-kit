package io.thernal.buildkit.buildlogic.modules.graph

import java.io.Serializable

/**
 * One module and the modules it declares a dependency on. [area] is the first directory under the
 * modules root (`app.modules.root`): `core` for `:core:network:api`, and for `:fixture:core:x` too
 * when the root is `fixture`.
 */
data class ModuleNode(
    val path: String,
    val area: String,
    val sourceDirectory: String,
    val dependencies: List<String>,
) : Serializable {

    /** `api`, `impl`, `wiring`, or null for a module that is not part of a capability split. */
    val role: String? get() = path.substringAfterLast(':').takeIf { it in ROLES }

    private companion object {
        val ROLES = setOf("api", "impl", "wiring")
        const val serialVersionUID = 1L
    }
}
