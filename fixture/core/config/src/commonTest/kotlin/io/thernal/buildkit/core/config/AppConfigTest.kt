package io.thernal.buildkit.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppConfigTest {
    @Test
    fun `reads the environment of the active flavor`() {
        assertTrue(AppConfig.flavor in setOf("regress", "dev", "beta", "prod"))
        assertEquals(AppConfig.flavor == "prod", AppConfig.isProduction)
        assertTrue(AppConfig.baseUrl.startsWith("http"))
    }
}
