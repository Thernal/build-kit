package io.thernal.buildkit.features.greeting.impl.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentGreeterTest {
    @Test
    fun `greets with the flavor`() {
        assertEquals("Hello, Ada, from beta", EnvironmentGreeter(flavor = "beta").greet("Ada"))
    }
}
