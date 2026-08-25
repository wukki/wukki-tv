package hu.wukki.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveChannelPreviewTest {
    private val channels = listOf("one", "two", "three")

    @Test
    fun `first arrow opens current channel without moving when panel is hidden`() {
        val result = LiveChannelPreviewState().reduce(
            LiveChannelPreviewEvent.NEXT,
            channels,
            activeChannelId = "two",
            panelVisible = false
        )

        assertEquals("two", result.state.channelId)
        assertIs<LiveChannelPreviewEffect.None>(result.effect)
        assertTrue(result.handled)
    }

    @Test
    fun `current channel can still be previewed when the filtered list is empty`() {
        val result = LiveChannelPreviewState().reduce(
            LiveChannelPreviewEvent.PREVIOUS,
            channelIds = emptyList(),
            activeChannelId = "two",
            panelVisible = false
        )

        assertEquals("two", result.state.channelId)
        assertTrue(result.handled)
    }

    @Test
    fun `first arrow moves immediately when information panel is already visible`() {
        val result = LiveChannelPreviewState().reduce(
            LiveChannelPreviewEvent.NEXT,
            channels,
            activeChannelId = "two",
            panelVisible = true
        )

        assertEquals("three", result.state.channelId)
    }

    @Test
    fun `arrows browse in both directions and wrap around`() {
        val fromLast = LiveChannelPreviewState("three").reduce(
            LiveChannelPreviewEvent.NEXT,
            channels,
            activeChannelId = "one",
            panelVisible = true
        )
        val fromFirst = LiveChannelPreviewState("one").reduce(
            LiveChannelPreviewEvent.PREVIOUS,
            channels,
            activeChannelId = "one",
            panelVisible = true
        )

        assertEquals("one", fromLast.state.channelId)
        assertEquals("three", fromFirst.state.channelId)
    }

    @Test
    fun `confirm is the only browsing event that requests a channel open`() {
        val preview = LiveChannelPreviewState("three")
        val browse = preview.reduce(LiveChannelPreviewEvent.PREVIOUS, channels, "one", panelVisible = true)
        val confirm = preview.reduce(LiveChannelPreviewEvent.CONFIRM, channels, "one", panelVisible = true)

        assertIs<LiveChannelPreviewEffect.None>(browse.effect)
        assertEquals("three", assertIs<LiveChannelPreviewEffect.OpenChannel>(confirm.effect).channelId)
        assertNull(confirm.state.channelId)
    }

    @Test
    fun `cancel and timeout discard preview without opening a channel`() {
        listOf(LiveChannelPreviewEvent.CANCEL, LiveChannelPreviewEvent.TIMEOUT).forEach { event ->
            val result = LiveChannelPreviewState("two").reduce(event, channels, "one", panelVisible = true)

            assertNull(result.state.channelId)
            assertIs<LiveChannelPreviewEffect.Dismiss>(result.effect)
            assertTrue(result.handled)
        }
    }

    @Test
    fun `confirm without a preview is ignored`() {
        val result = LiveChannelPreviewState().reduce(
            LiveChannelPreviewEvent.CONFIRM,
            channels,
            activeChannelId = "one",
            panelVisible = true
        )

        assertFalse(result.handled)
        assertIs<LiveChannelPreviewEffect.None>(result.effect)
    }
}
