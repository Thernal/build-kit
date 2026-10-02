package io.thernal.buildkit.buildlogic

/** Gradle property: the directory modules live under, relative to the repository root ("" = root). */
internal const val MODULES_ROOT_PROPERTY = "app.modules.root"

/** Gradle property: the top-level directories (under the modules root) that hold modules. */
internal const val MODULES_AREAS_PROPERTY = "app.modules.areas"

internal const val DEFAULT_MODULE_AREAS = "apps,core,designsystem,features"

/**
 * The plugin accessor prefix generated build files use. Written out whole, so a kit install's alias
 * rename (`libs.plugins.<alias>.`) reaches it.
 */
private const val PLUGIN_ACCESSOR = "libs.plugins.buildkit."

/** Where modules live: an optional root directory and the areas under it. */
internal data class ModuleLayout(
    val root: List<String>,
    val areas: Set<String>,
) {
    /** The area a bare name (`profile`) goes to. */
    val defaultArea: String get() = if ("features" in areas) "features" else areas.first()

    companion object {
        fun parse(root: String?, areas: String?): ModuleLayout = ModuleLayout(
            root = root.orEmpty().split('/', ':').map(String::trim).filter(String::isNotEmpty),
            areas = (areas?.takeIf(String::isNotBlank) ?: DEFAULT_MODULE_AREAS)
                .split(',').map(String::trim).filter(String::isNotEmpty).toCollection(linkedSetOf()),
        )
    }
}

/**
 * Pure scaffolding rules behind the `create` task, free of Gradle state so they can be unit tested.
 * Generated modules are plain multiplatform modules — or plain Android libraries in an Android-only
 * application; Compose or anything else is added by hand when a module actually needs it.
 */
internal object ModuleScaffold {
    private val SEGMENT_PATTERN = Regex("""^[a-z][a-z0-9]*(-[a-z0-9]+)*$""")

    /**
     * A user-supplied spec as path segments from the repository root. A single segment goes to the
     * default area (`profile` → `features/profile`); a path is resolved from the modules root.
     */
    fun parseSpec(rawSpec: String, layout: ModuleLayout): List<String> {
        val segments = rawSpec.replace(':', '/').split('/').map(String::trim).filter(String::isNotEmpty)
        require(segments.isNotEmpty()) { "Module path is empty." }
        segments.forEach { segment ->
            require(SEGMENT_PATTERN.matches(segment)) {
                "Invalid module path segment '$segment'. Use lowercase letters, digits, and single dashes."
            }
        }
        // Only a bare name goes to the default area; anything written as a path (`features/`) is one.
        val isPath = rawSpec.contains('/') || rawSpec.contains(':')
        val resolved = if (!isPath) listOf(layout.defaultArea) + segments else segments
        require(resolved.first() in layout.areas) {
            "'${resolved.first()}' is not a module area. Expected one of ${layout.areas.joinToString()} " +
                "($MODULES_AREAS_PROPERTY)."
        }
        require(resolved.size >= 2) { "Module path '$rawSpec' needs at least an area and a name." }
        return layout.root + resolved
    }

    /** `:features:profile:api`. */
    fun projectPath(segments: List<String>): String = segments.joinToString(separator = ":", prefix = ":")

    /** `projects.features.profile.api`, with dashed segments camel-cased as Gradle does. */
    fun projectAccessor(segments: List<String>): String =
        segments.joinToString(separator = ".", prefix = "projects.", transform = ::toCamelCase)

    /** `src/commonMain/kotlin/com/example/features/profile/api`. */
    fun sourceDirectory(namespacePrefix: String, segments: List<String>, sourceSet: String): String {
        val packagePath = namespaceFor(namespacePrefix, projectPath(segments)).replace('.', '/')
        return "src/$sourceSet/kotlin/$packagePath"
    }

    /** The source set a module's code goes in: `commonMain`, or `main` for an Android library. */
    fun mainSourceSet(androidOnly: Boolean): String = if (androidOnly) "main" else "commonMain"

    /** The module build file. Sibling dependencies are declared only for siblings that exist. */
    fun buildFile(
        segments: List<String>,
        kind: ModuleKind,
        siblings: Set<ModuleKind>,
        androidOnly: Boolean = false,
    ): String {
        val featureSegments = segments.dropLast(1)
        val dependencies = kind.dependsOn
            .filter { it in siblings }
            .map { "implementation(${projectAccessor(featureSegments + it.directoryName)})" }
        return buildString {
            appendLine("plugins {")
            kind.plugins(androidOnly).forEach { plugin -> appendLine("    alias($PLUGIN_ACCESSOR$plugin)") }
            appendLine("}")
            if (dependencies.isNotEmpty() && androidOnly) {
                appendLine()
                appendLine("dependencies {")
                dependencies.forEach { appendLine("    $it") }
                appendLine("}")
            } else if (dependencies.isNotEmpty()) {
                appendLine()
                appendLine("kotlin {")
                appendLine("    sourceSets {")
                appendLine("        commonMain.dependencies {")
                dependencies.forEach { appendLine("            $it") }
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
            }
        }
    }

    /**
     * The Metro binding container of a `wiring` module. `impl` reaches it through `implementation`,
     * and Metro does not aggregate contributions reached that way, so the bindings are declared here
     * — which is also what keeps the implementation off an application's compile classpath.
     */
    fun bindingContainer(namespacePrefix: String, segments: List<String>): String {
        val name = bindingContainerName(segments.dropLast(1))
        return """
            |package ${namespaceFor(namespacePrefix, projectPath(segments))}
            |
            |import dev.zacsweers.metro.AppScope
            |import dev.zacsweers.metro.BindingContainer
            |import dev.zacsweers.metro.ContributesTo
            |
            |/**
            | * Metro bindings this capability contributes to an application graph. Add `@Provides`
            | * functions in a companion object that return the `api` contract and construct the `impl`:
            | *
            | * ```
            | * companion object {
            | *     @Provides
            | *     @SingleIn(AppScope::class)
            | *     fun provideExample(): Example {
            | *         return ExampleImpl()
            | *     }
            | * }
            | * ```
            | */
            |@BindingContainer
            |@ContributesTo(AppScope::class)
            |interface $name
            |
        """.trimMargin()
    }

    /** `features/shared/auth` → `AuthWiring`. */
    fun bindingContainerName(featureSegments: List<String>): String =
        featureSegments.lastOrNull().orEmpty().split('-').filter(String::isNotEmpty)
            .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } + "Wiring"

    private fun toCamelCase(segment: String): String = segment.split('-').filter(String::isNotEmpty)
        .mapIndexed { index, part -> if (index == 0) part else part.replaceFirstChar(Char::uppercaseChar) }
        .joinToString("")
}

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
