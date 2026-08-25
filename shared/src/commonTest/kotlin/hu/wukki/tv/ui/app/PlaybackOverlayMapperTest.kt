package hu.wukki.tv.ui.app

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.PlaybackState
import hu.wukki.tv.Programme
import hu.wukki.tv.ui.navigation.DashboardSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackOverlayMapperTest {
    private val channel = Channel(
        id = "channel",
        playlistId = "playlist",
        name = "TV",
        streamUrl = "https://example.com/live.m3u8",
        tvgId = "tv",
        tvgName = "TV",
        group = "General",
        logo = "https://example.com/logo.png"
    )
    private val programme = Programme(
        channelId = "tv",
        title = "Programme",
        start = 1_000L,
        end = 61_000L,
        imageUrl = "https://example.com/programme.jpg"
    )

    @Test
    fun `programme image is exposed only on live screen when enabled`() {
        assertEquals(programme.imageUrl, overlay(DashboardSection.LIVE, showImages = true).programmeImageUrl)
        assertNull(overlay(DashboardSection.CHANNELS, showImages = true).programmeImageUrl)
        assertNull(overlay(DashboardSection.GUIDE, showImages = true).programmeImageUrl)
        assertNull(overlay(DashboardSection.LIVE, showImages = false).programmeImageUrl)
    }

    private fun overlay(section: DashboardSection, showImages: Boolean) = playbackOverlayData(
        channel = channel,
        currentProgramme = programme,
        nextProgramme = null,
        now = 2_000L,
        section = section,
        showProgrammeInfo = true,
        channelNumberInput = "",
        language = AppLanguage.HUNGARIAN,
        showLogos = true,
        showProgrammeImages = showImages,
        playbackState = PlaybackState.PLAYING,
        playbackDetail = null
    )
}
