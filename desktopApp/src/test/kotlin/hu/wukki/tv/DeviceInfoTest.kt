package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceInfoTest {
    @Test
    fun `formats storage sizes for user-facing display`() {
        assertEquals("0 B", formatByteSize(0))
        assertEquals("1.0 KB", formatByteSize(1024))
        assertEquals("1.5 MB", formatByteSize(1_572_864))
        assertEquals("0 B", formatByteSize(-1))
    }
}
