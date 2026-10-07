package io.thernal.buildkit.buildlogic

import io.thernal.buildkit.buildlogic.compose.stability.ModuleStability
import io.thernal.buildkit.buildlogic.compose.stability.nonSkippableComposables
import io.thernal.buildkit.buildlogic.compose.stability.parseModuleMetrics
import io.thernal.buildkit.buildlogic.compose.stability.preferredMetricsFile
import io.thernal.buildkit.buildlogic.compose.stability.renderModuleStability
import io.thernal.buildkit.buildlogic.compose.stability.renderStabilityIndex
import io.thernal.buildkit.buildlogic.compose.stability.stabilityReportName
import io.thernal.buildkit.buildlogic.compose.stability.unstableClasses
import io.thernal.buildkit.buildlogic.modules.graph.isProductionDependencyConfiguration
import io.thernal.buildkit.buildlogic.modules.scaffold.ModuleKind
import io.thernal.buildkit.buildlogic.modules.scaffold.ModuleLayout
import io.thernal.buildkit.buildlogic.modules.scaffold.ModuleScaffold
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleToolingTest {
    private val layout = ModuleLayout.parse(root = null, areas = null)

    @Test
    fun `a bare name goes to features, a path to its area, under the modules root`() {
        assertEquals(listOf("features", "profile"), ModuleScaffold.parseSpec("profile", layout))
        assertEquals(listOf("core", "network"), ModuleScaffold.parseSpec("core/network", layout))
        val rooted = ModuleLayout.parse(root = "fixture", areas = "core,features")
        assertEquals(listOf("fixture", "features", "profile"), ModuleScaffold.parseSpec("profile", rooted))
    }

    @Test
    fun `rejects unknown areas and malformed segments`() {
        assertFailsWith<IllegalArgumentException> { ModuleScaffold.parseSpec("somewhere/profile", layout) }
        assertFailsWith<IllegalArgumentException> { ModuleScaffold.parseSpec("Profile", layout) }
        assertFailsWith<IllegalArgumentException> { ModuleScaffold.parseSpec("features/", layout) }
    }

    @Test
    fun `wiring depends on the siblings that exist, with injection`() {
        val file = ModuleScaffold.buildFile(
            segments = listOf("features", "venue-management", "wiring"),
            kind = ModuleKind.WIRING,
            siblings = setOf(ModuleKind.API, ModuleKind.WIRING),
        )
        assertTrue("alias(libs.plugins.buildkit.injection)" in file)
        assertTrue("implementation(projects.features.venueManagement.api)" in file)
        assertFalse("impl)" in file)
        assertFalse("api(" in file.replace(".api)", ""))
    }

    @Test
    fun `an api module has no dependencies block`() {
        val file = ModuleScaffold.buildFile(listOf("features", "profile", "api"), ModuleKind.API, setOf(ModuleKind.API))
        assertFalse("kotlin {" in file)
    }

    @Test
    fun `names follow the path`() {
        assertEquals("VenueManagementWiring", ModuleScaffold.bindingContainerName(listOf("features", "venue-management")))
        assertEquals(
            "src/commonMain/kotlin/com/example/features/profile/api",
            ModuleScaffold.sourceDirectory("com.example", listOf("features", "profile", "api"), "commonMain"),
        )
    }

    @Test
    fun `production dependency buckets exclude every test form`() {
        listOf("implementation", "api", "commonMainImplementation", "androidMainApi", "iosMainCompileOnly", "debugImplementation")
            .forEach { assertTrue(isProductionDependencyConfiguration(it), it) }
        listOf("testImplementation", "commonTestImplementation", "androidHostTestImplementation", "kapt", "detektPlugins", "apiElements")
            .forEach { assertFalse(isProductionDependencyConfiguration(it), it) }
    }

    @Test
    fun `stability reads the android metrics and both report lists`() {
        val build = kotlin.io.path.createTempDirectory("stability").toFile()
        build.resolve("compose-metrics/iosArm64/main").apply { mkdirs() }.resolve("m-module.json").writeText("{\"totalComposables\": 9}")
        val android = build.resolve("compose-metrics/android/main").apply { mkdirs() }.resolve("m-module.json")
        android.writeText("{ \"totalComposables\": 3, \"skippableComposables\" : 2 }")
        val reports = build.resolve("compose-reports").apply { mkdirs() }
        reports.resolve("m-classes.txt").writeText("unstable class com.example.State {\n}\nstable class com.example.Ok {\n}\n")
        reports.resolve("m-composables.txt").writeText(
            "restartable skippable scheme(\"x\") fun com.example.Fine(\n)\nrestartable scheme(\"x\") fun com.example.Slow(\n  unstable state: State\n)\n",
        )

        assertEquals(android, preferredMetricsFile(build.resolve("compose-metrics")))
        assertEquals(mapOf("totalComposables" to 3, "skippableComposables" to 2), parseModuleMetrics(android.readText()))
        assertEquals(listOf("com.example.State"), unstableClasses(reports))
        assertEquals(listOf("com.example.Slow"), nonSkippableComposables(reports))
        build.deleteRecursively()
    }

    @Test
    fun `each module gets its own report file, linked from the index`() {
        assertEquals("features-profile-impl.md", stabilityReportName(":features:profile:impl"))
        val page = renderModuleStability(ModuleStability(":a:b", mapOf("totalComposables" to 2), listOf("X"), listOf("Y")))
        assertTrue("- `X`" in page && "- `Y`" in page)
        val index = renderStabilityIndex(listOf(":a:b" to mapOf("totalComposables" to 2, "skippableComposables" to 1)))
        assertTrue("[`:a:b`](a-b.md)" in index)
        assertTrue("50.0%" in index)
    }
}
