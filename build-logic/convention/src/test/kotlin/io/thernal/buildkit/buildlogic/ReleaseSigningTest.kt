package io.thernal.buildkit.buildlogic

import io.thernal.buildkit.buildlogic.application.releaseSigningDirectory
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseSigningTest {
    private val root = Files.createTempDirectory("repository").toFile()

    @Test
    fun `an app's own keystore wins over the shared one`() {
        keystore("config/signing/release")
        keystore("config/signing/release/customer")
        assertEquals(root.resolve("config/signing/release/customer"), releaseSigningDirectory(root, "customer"))
    }

    @Test
    fun `an app without its own takes the shared keystore`() {
        keystore("config/signing/release")
        keystore("config/signing/release/partner")
        assertEquals(root.resolve("config/signing/release"), releaseSigningDirectory(root, "customer"))
    }

    @Test
    fun `a folder without keystore properties is not a keystore`() {
        root.resolve("config/signing/release/customer").mkdirs()
        assertNull(releaseSigningDirectory(root, "customer"))
    }

    private fun keystore(path: String): File =
        root.resolve(path).apply { mkdirs() }.resolve("keystore.properties").apply { writeText("storeFile=a.jks\n") }
}
