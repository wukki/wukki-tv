package hu.wukki.tv

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VlcRuntimeTest {
    @Test
    fun `does not use VLCs removed plugin path option`() {
        val runtime = VlcRuntime(File("/runtime/vlc"), File("/runtime/vlc/plugins"))

        assertFalse(runtime.factoryArguments.any { it.startsWith("--plugin-path=") })
    }

    @Test
    fun `does not add a plugin path when no directory is available`() {
        val runtime = VlcRuntime(File("/runtime/vlc"), null)

        assertFalse(runtime.factoryArguments.any { it.startsWith("--plugin-path=") })
    }

    @Test
    fun `uses software decoding for callback rendering`() {
        val runtime = VlcRuntime(File("/runtime/vlc"), null)

        assertEquals("--avcodec-hw=none", runtime.factoryArguments.first { it.startsWith("--avcodec-hw=") })
    }

    @Test
    fun `forces a fresh plugin scan for copied runtimes`() {
        val runtime = VlcRuntime(File("/runtime/vlc"), null)

        assertFalse("--plugins-cache" in runtime.factoryArguments)
        assertEquals("--no-plugins-cache", runtime.factoryArguments.first { it.endsWith("plugins-cache") })
    }
}
