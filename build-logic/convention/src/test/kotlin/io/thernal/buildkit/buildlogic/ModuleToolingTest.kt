package io.thernal.buildkit.buildlogic

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
    fun `stability metrics map back to their module and reports`() {
        val root = File("/repo")
        val metrics = File("/repo/features/x/impl/build/compose-metrics/android/main/impl-module.json")
        assertEquals(":features:x:impl", moduleGradlePath(metrics, root))
        assertEquals(File("/repo/features/x/impl/build/compose-reports/android/main"), reportsDirectoryFor(metrics))
        assertEquals(mapOf("totalComposables" to 3, "skippableComposables" to 2), parseModuleMetrics("{ \"totalComposables\": 3, \"skippableComposables\" : 2 }"))
    }
}
