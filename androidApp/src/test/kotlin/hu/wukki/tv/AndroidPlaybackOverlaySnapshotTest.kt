package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidPlaybackOverlaySnapshotTest {
    @Test
    fun `buffering snapshot contains spinner and label`() {
        assertEquals(
            AndroidBufferingOverlaySnapshot(spinner = true, label = "Buffering…"),
            androidBufferingOverlaySnapshot(bufferingOverlayData("Buffering…"))
        )
    }
}

internal fun bufferingOverlayData(label: String) = PlaybackOverlayData(
    channelId = "channel",
    channelNumber = "1",
    channelName = "TV",
    logoUrl = null,
    programmeImageUrl = null,
    showProgrammeInfo = false,
    showPreviewLogo = false,
    channelNumberInput = null,
    programme = PlaybackProgrammeOverlay(title = "No EPG"),
    buffering = PlaybackBufferingOverlay(label)
)
