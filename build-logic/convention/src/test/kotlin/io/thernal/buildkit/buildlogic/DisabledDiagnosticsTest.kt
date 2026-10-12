package io.thernal.buildkit.buildlogic

import io.thernal.buildkit.buildlogic.quality.disabledDiagnosticsIn
import kotlin.test.Test
import kotlin.test.assertEquals

class DisabledDiagnosticsTest {
    @Test
    fun `the listed diagnostics are read in order`() {
        val yaml = """
            comments:
              active: true
            analysis:
              disabledDiagnostics:
                - ACTUAL_WITHOUT_EXPECT
                - EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE
            style:
              active: true
        """.trimIndent()

        assertEquals(
            expected = listOf("ACTUAL_WITHOUT_EXPECT", "EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE"),
            actual = disabledDiagnosticsIn(yaml),
        )
    }

    @Test
    fun `comments and blank lines inside the list are skipped`() {
        val yaml = "analysis:\n  disabledDiagnostics:\n    # why\n    - ONE  # trailing\n\n    - TWO\nstyle:\n  - NOT_THIS\n"

        assertEquals(expected = listOf("ONE", "TWO"), actual = disabledDiagnosticsIn(yaml))
    }

    @Test
    fun `no section means nothing is disabled`() {
        assertEquals(expected = emptyList(), actual = disabledDiagnosticsIn("style:\n  active: true\n"))
    }
}
