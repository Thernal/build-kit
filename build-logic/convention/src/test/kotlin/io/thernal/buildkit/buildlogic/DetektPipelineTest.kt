package io.thernal.buildkit.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetektPipelineTest {
    @Test
    fun `counts each multiplatform source once`() {
        listOf("detektMainAndroid", "detektHostTestAndroid", "detektIosMainSourceSet", "detektAppleTestSourceSet")
            .forEach { assertTrue(isCountedMultiplatformAnalysis(it), it) }
        listOf("detekt", "detektCommonMainSourceSet", "detektAndroidMainSourceSet", "detektAndroidHostTestSourceSet")
            .forEach { assertFalse(isCountedMultiplatformAnalysis(it), it) }
    }

    @Test
    fun `counts the JVM compilations only`() {
        assertTrue(isCountedJvmAnalysis("detektMain"))
        assertTrue(isCountedJvmAnalysis("detektTest"))
        assertFalse(isCountedJvmAnalysis("detektMainSourceSet"))
        assertFalse(isCountedJvmAnalysis("detekt"))
    }

    @Test
    fun `clears markers and leaves clean files alone`() {
        assertEquals("val a = 1\nval b = 2\n", clearDetektTodos("val a = 1 // TODO: Detekt [MagicNumber: x]\nval b = 2\n"))
        assertNull(clearDetektTodos("val a = 1\n"))
    }

    @Test
    fun `annotation replaces an old marker and sorts labels`() {
        val annotated = annotate("a // TODO: Detekt [Old]\nb\n", mapOf(1 to setOf("Z: z", "A: a")))
        assertEquals("a // TODO: Detekt [A: a, Z: z]\nb\n", annotated)
    }

    @Test
    fun `a scoped merge replaces only scanned files and drops stale ones`() {
        val previous = mapOf("/a.kt" to "<file name=\"/a.kt\">old</file>", "/b.kt" to "B", "/gone.kt" to "G")
        val merged = mergeBaseline(
            previous = previous,
            fresh = mapOf("/a.kt" to "<file name=\"/a.kt\">new</file>"),
            scanned = setOf("/a.kt", "/clean.kt"),
            stale = setOf("/gone.kt"),
        )
        assertTrue("new" in merged)
        assertFalse("old" in merged)
        assertTrue("B" in merged)
        assertFalse("G" in merged)
    }

    @Test
    fun `a full merge replaces everything`() {
        val merged = mergeBaseline(mapOf("/b.kt" to "B"), mapOf("/a.kt" to "A"), scanned = null, stale = emptySet())
        assertTrue("A" in merged)
        assertFalse("B" in merged)
    }

    @Test
    fun `report paths become absolute`() {
        val root = File("/repo")
        val blocks = fileBlocks("<file name=\"src/A.kt\">\n</file>") { resolveAgainst(root, it).path }
        val (path, block) = blocks.entries.single()
        assertEquals(File("/repo/src/A.kt").canonicalPath, path)
        assertTrue("name=\"$path\"" in block)
    }

    @Test
    fun `hooks path is added, rewritten, or left alone`() {
        assertEquals("[core]\n\thooksPath = .githooks\n", withHooksPath(""))
        assertEquals("[core]\n\thooksPath = .githooks\n", withHooksPath("[core]\n\thooksPath = hooks\n"))
        assertNull(withHooksPath("[core]\n\thooksPath = .githooks\n"))
    }

    @Test
    fun `the report merge driver is declared once`() {
        val once = withReportMergeDriver("[core]\n\tbare = false")!!
        assertTrue("[merge \"generated-report\"]" in once)
        assertTrue("driver = true" in once)
        assertNull(withReportMergeDriver(once))
    }
}
