package hu.wukki.tv.ui.guide

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.Programme
import hu.wukki.tv.ui.navigation.RemoteKey
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideNavigationTest {
    private val now =
        LocalDate
            .of(2026, 9, 14)
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    private val timeline = guideTimeline(now, now + 3 * 86_400_000L)
    private val active = channel("active", false)
    private val favorite = channel("favorite", true)
    private val data =
        object : GuideDataSource {
            override val language = AppLanguage.ENGLISH
            override val selectedChannelId = active.id
            override val showLogos = false

            override fun channels() = listOf(favorite, active)

            override fun latestProgrammeEnd() = timeline.end

            override fun programmesFor(
                channel: Channel,
                from: Long,
                to: Long,
            ) = listOf(
                Programme(channel.id, "Current", now - 600_000, now + 600_000),
                Programme(channel.id, "Next", now + 600_000, now + 1_200_000),
            )
        }

    @Test
    fun `empty programme row has a leftward return to channel column`() =
        runBlocking {
            val noEpg =
                object : GuideDataSource by data {
                    override fun programmesFor(
                        channel: Channel,
                        from: Long,
                        to: Long,
                    ) = emptyList<Programme>()
                }
            val state = state()
            state.selectChannel(active, noEpg, timeline)
            assertTrue(state.confirm(noEpg, timeline, now))
            state.handleRemoteKey(RemoteKey.LEFT, noEpg, this, timeline)
            yield()
            assertEquals(GuideFocusZone.CHANNELS, state.navigation.zone)
            assertEquals(active.id, state.focusedChannelId)
        }

    @Test
    fun `one confirm on entry focuses now and a second can open its details`() =
        runBlocking {
            val state = state()
            state.focusCurrentProgramme(data, timeline, now)
            assertEquals(GuideFocusZone.HEADER, state.navigation.zone)
            assertTrue(state.confirm(data, timeline, now))
            assertEquals(GuideFocusZone.PROGRAMMES, state.navigation.zone)
            assertEquals(active.id, state.focusedChannelId)
            assertEquals(0, state.guideOpenRequest)
            assertFalse(state.confirm(data, timeline, now))
        }

    @Test
    fun `now clears favorites and returns to active channel`() =
        runBlocking {
            val state = state()
            state.navigation.favoritesOnly = true
            state.selectProgramme(favorite, data.programmesFor(favorite, 0, 0).last())
            state.activateHeader(GuideHeaderAction.NOW, data, timeline, now)
            assertFalse(state.navigation.favoritesOnly)
            assertEquals(active.id, state.focusedChannelId)
            assertEquals("Current", state.focusedProgramme(data, timeline)?.second?.title)
            assertEquals(now, state.focusTime)
            assertEquals(1, state.verticalList.firstVisibleItemIndex)
        }

    @Test
    fun `filter preserves time and horizontal offset and falls back to visible channel`() =
        runBlocking {
            val state = state()
            state.selectProgramme(active, data.programmesFor(active, 0, 0).last())
            val time = state.focusTime
            state.activateHeader(GuideHeaderAction.FAVORITES, data, timeline, now)
            assertEquals(listOf(favorite), state.channels(data))
            assertEquals(favorite.id, state.focusedChannelId)
            assertEquals(time, state.focusTime)
            assertEquals(720, state.horizontalScroll.value)
            state.activateHeader(GuideHeaderAction.ALL, data, timeline, now)
            assertEquals(favorite.id, state.focusedChannelId)
            assertEquals(720, state.horizontalScroll.value)
        }

    @Test
    fun `empty favorites keeps header reachable and all restores channels`() =
        runBlocking {
            val emptyFavorites =
                object : GuideDataSource by data {
                    override fun channels() = listOf(active)
                }
            val state = state()
            state.activateHeader(GuideHeaderAction.FAVORITES, emptyFavorites, timeline, now)
            assertNull(state.focusedChannelId)
            assertNull(state.focusedProgramme(data, timeline))
            assertEquals(GuideFocusZone.HEADER, state.navigation.zone)
            state.navigation.moveFocus(RemoteKey.DOWN, -1)
            assertEquals(GuideFocusZone.HEADER, state.navigation.zone)
            state.activateHeader(GuideHeaderAction.ALL, emptyFavorites, timeline, now)
            assertEquals(active.id, state.focusedChannelId)
        }

    @Test
    fun `header channel and programme focus have reversible boundaries`() {
        val navigation = GuideNavigationState()
        navigation.moveFocus(RemoteKey.RIGHT, 0)
        assertEquals(GuideHeaderAction.TONIGHT, navigation.action)
        navigation.moveFocus(RemoteKey.DOWN, 0)
        assertEquals(GuideFocusZone.CHANNELS, navigation.zone)
        navigation.moveFocus(RemoteKey.RIGHT, 0)
        assertEquals(GuideFocusZone.PROGRAMMES, navigation.zone)
        navigation.moveFocus(RemoteKey.UP, 0)
        assertEquals(GuideFocusZone.CHANNELS, navigation.zone)
        navigation.moveFocus(RemoteKey.UP, 0)
        assertEquals(GuideFocusZone.HEADER, navigation.zone)
    }

    @Test
    fun `reinitializing retained guide leaves exact selected cell and scroll positions intact`() =
        runBlocking {
            val state = state()
            state.initialise(data, data.channels(), timeline)
            state.selectProgramme(active, data.programmesFor(active, 0, 0).last())
            state.verticalList.requestScrollToItem(1, 19)
            val key = state.focusedProgrammeKey
            state.initialise(data, data.channels(), timeline)
            assertEquals(key, state.focusedProgrammeKey)
            assertEquals(GuideFocusZone.PROGRAMMES, state.navigation.zone)
            assertEquals(1, state.verticalList.firstVisibleItemIndex)
            assertEquals(19, state.verticalList.firstVisibleItemScrollOffset)
            assertEquals(720, state.horizontalScroll.value)
            assertFalse(state.confirm(data, timeline, now))
        }

    @Test
    fun `tonight uses local twenty hundred on both clock change days`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Budapest"))
            listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25)).forEach { day ->
                val zone = ZoneId.systemDefault()
                val instant =
                    day
                        .atTime(12, 0)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                val target = GuideNavigationState().targetTime(GuideHeaderAction.TONIGHT, guideTimeline(instant, null), instant, instant)
                assertEquals(
                    day
                        .atTime(20, 0)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli(),
                    target,
                )
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `day selection preserves local time and stops at timeline boundaries`() {
        val navigation = GuideNavigationState()
        val next = navigation.targetTime(GuideHeaderAction.NEXT_DAY, timeline, now, now)
        assertTrue(next > now)
        assertEquals(now, navigation.targetTime(GuideHeaderAction.PREVIOUS_DAY, timeline, now, next))
        assertEquals(now, navigation.targetTime(GuideHeaderAction.PREVIOUS_DAY, timeline, now, now))
        assertEquals(now, navigation.targetTime(GuideHeaderAction.NEXT_DAY, guideTimeline(now, null), now, now))
    }

    private fun state() = EpgGuideState(ScrollState(720), LazyListState())

    private fun channel(
        id: String,
        favorite: Boolean,
    ) = Channel(
        id = id,
        playlistId = "playlist",
        name = id,
        streamUrl = "https://example.test/$id",
        tvgId = null,
        tvgName = null,
        group = "group",
        logo = null,
        favorite = favorite,
    )
}
