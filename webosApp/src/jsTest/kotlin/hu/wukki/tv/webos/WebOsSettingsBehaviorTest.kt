package hu.wukki.tv.webos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebOsSettingsBehaviorTest {
    @Test
    fun `manual refresh has no timer`() {
        assertNull(refreshDelayMillis(hours = 0, updatedAt = 1_000, now = 2_000))
    }

    @Test
    fun `scheduled refresh uses remaining interval`() {
        val hour = 60L * 60L * 1_000L
        assertEquals((5 * hour).toInt(), refreshDelayMillis(hours = 6, updatedAt = hour, now = 2 * hour))
    }

    @Test
    fun `overdue refresh runs promptly`() {
        assertEquals(1_000, refreshDelayMillis(hours = 6, updatedAt = 0, now = 24L * 60L * 60L * 1_000L))
    }
}
