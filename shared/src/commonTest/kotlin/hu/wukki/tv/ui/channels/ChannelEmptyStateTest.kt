package hu.wukki.tv.ui.channels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChannelEmptyStateTest {
    @Test
    fun `visible channels have no empty state`() {
        assertNull(channelEmptyState(true, 1, "", false, null, false))
    }

    @Test
    fun `filters take priority over source failures`() {
        assertEquals(
            ChannelEmptyState.NO_SEARCH_RESULTS,
            channelEmptyState(false, 0, "news", true, "Sport", true),
        )
        assertEquals(
            ChannelEmptyState.NO_FAVORITES,
            channelEmptyState(true, 0, "", true, null, false),
        )
        assertEquals(
            ChannelEmptyState.NO_CATEGORY_RESULTS,
            channelEmptyState(true, 0, "", false, "Sport", false),
        )
    }

    @Test
    fun `unavailable and not yet loaded playlists remain distinct`() {
        assertEquals(
            ChannelEmptyState.LOAD_FAILED,
            channelEmptyState(false, 0, "", false, null, true),
        )
        assertEquals(
            ChannelEmptyState.NO_DATA,
            channelEmptyState(false, 0, "", false, null, false),
        )
    }

    @Test
    fun `each empty state exposes its recovery action`() {
        assertEquals(ChannelEmptyAction.REFRESH, ChannelEmptyState.NO_DATA.action())
        assertEquals(ChannelEmptyAction.REFRESH, ChannelEmptyState.LOAD_FAILED.action())
        assertEquals(ChannelEmptyAction.CLEAR_SEARCH, ChannelEmptyState.NO_SEARCH_RESULTS.action())
        assertEquals(ChannelEmptyAction.SHOW_ALL, ChannelEmptyState.NO_FAVORITES.action())
        assertEquals(ChannelEmptyAction.SHOW_ALL, ChannelEmptyState.NO_CATEGORY_RESULTS.action())
    }
}
