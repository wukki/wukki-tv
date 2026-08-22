package hu.wukki.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class FocusMemoryTest {
    private val channels = listOf("rtl", "tv2", "m4")

    @Test
    fun `saved visible channel is restored before the current selection`() {
        assertEquals(
            2,
            restoredChannelIndex(
                channelIds = channels,
                savedChannelId = "m4",
                selectedChannelId = "rtl",
                fallbackIndex = 0
            )
        )
    }

    @Test
    fun `current selection is used when the saved channel is no longer visible`() {
        assertEquals(
            1,
            restoredChannelIndex(
                channelIds = channels,
                savedChannelId = "removed-channel",
                selectedChannelId = "tv2",
                fallbackIndex = 0
            )
        )
    }

    @Test
    fun `fallback index is clamped after a filter removes the saved channel`() {
        assertEquals(
            2,
            restoredChannelIndex(
                channelIds = channels,
                savedChannelId = "removed-channel",
                selectedChannelId = "also-removed",
                fallbackIndex = 99
            )
        )
        assertEquals(
            0,
            restoredChannelIndex(
                channelIds = channels,
                savedChannelId = null,
                selectedChannelId = null,
                fallbackIndex = -4
            )
        )
    }

    @Test
    fun `entering channels prioritises the active visible channel`() {
        assertEquals(1, activeChannelIndex(channels, "tv2"))
        assertEquals(0, activeChannelIndex(channels, "not-visible"))
        assertEquals(0, activeChannelIndex(emptyList(), "tv2"))
    }
}
