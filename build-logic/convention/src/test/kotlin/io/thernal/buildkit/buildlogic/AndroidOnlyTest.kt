package io.thernal.buildkit.buildlogic

import io.thernal.buildkit.buildlogic.application.appDirectory
import io.thernal.buildkit.buildlogic.flavor.applicationIdSuffix
import io.thernal.buildkit.buildlogic.modules.scaffold.ModuleKind
import io.thernal.buildkit.buildlogic.modules.scaffold.ModuleScaffold
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidOnlyTest {
    @Test
    fun `only a platform list of android alone is Android-only`() {
        assertTrue(isAndroidOnly("android"))
        assertTrue(isAndroidOnly(" Android "))
        assertFalse(isAndroidOnly(null))
        assertFalse(isAndroidOnly(""))
        assertFalse(isAndroidOnly("android,ios"))
    }

    @Test
    fun `an android part of an app takes the app directory, a whole app module its own`() {
        assertEquals(File("apps/customer"), appDirectory(File("apps/customer/android")))
        assertEquals(File("apps/customer"), appDirectory(File("apps/customer")))
    }

    @Test
    fun `a flavor's id suffix is its name unless overridden`() {
        assertEquals("beta", applicationIdSuffix("beta", override = null))
        assertEquals("beta", applicationIdSuffix("beta", override = " "))
        assertEquals("dev", applicationIdSuffix("staging", override = "dev"))
        assertEquals("dev", applicationIdSuffix("staging", override = ".dev"))
    }

    @Test
    fun `an Android-only scaffold is an Android library with plain dependencies`() {
        val file = ModuleScaffold.buildFile(
            segments = listOf("features", "profile", "wiring"),
            kind = ModuleKind.WIRING,
            siblings = setOf(ModuleKind.API, ModuleKind.IMPL, ModuleKind.WIRING),
            androidOnly = true,
        )
        assertTrue("alias(libs.plugins.buildkit.android.library)" in file)
        assertTrue("alias(libs.plugins.buildkit.injection)" in file)
        assertFalse("kmp.library" in file)
        assertFalse("commonMain" in file)
        assertTrue("dependencies {\n    implementation(projects.features.profile.api)" in file)
        assertEquals("main", ModuleScaffold.mainSourceSet(androidOnly = true))
        assertEquals("commonMain", ModuleScaffold.mainSourceSet(androidOnly = false))
    }
}
