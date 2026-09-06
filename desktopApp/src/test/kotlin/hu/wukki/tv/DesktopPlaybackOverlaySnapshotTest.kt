package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlaybackOverlaySnapshotTest {
    @Test
    fun `buffering snapshot contains spinner and label`() {
        val data = PlaybackOverlayData(
            channelId = "channel",
            channelNumber = "1",
            channelName = "TV",
            logoUrl = null,
            programmeImageUrl = null,
            showProgrammeInfo = false,
            showPreviewLogo = false,
            channelNumberInput = null,
            programme = PlaybackProgrammeOverlay(title = "No EPG"),
            buffering = PlaybackBufferingOverlay("Buffering…")
        )

        assertEquals(
            DesktopBufferingOverlaySnapshot(spinner = true, label = "Buffering…"),
            desktopBufferingOverlaySnapshot(data)
        )
    }
}
