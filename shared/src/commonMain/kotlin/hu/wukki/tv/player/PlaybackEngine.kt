package hu.wukki.tv

/**
 * Platform-neutral playback contract consumed by the Compose application layer.
 * Desktop provides it through libVLC, Android through Media3/ExoPlayer.
 */
interface PlaybackEngine {
    val state: PlaybackState
    val detail: String?
    val successfullyPlayedChannelId: String?

    fun play(channel: Channel?, settings: PlaybackSettings, showLogos: Boolean = true, language: AppLanguage = AppLanguage.HUNGARIAN)
    fun updateSettings(settings: PlaybackSettings)
    fun updateOverlay(data: PlaybackOverlayData)
    fun stop()
    fun release()
}

/** Live-video gestures are supplied by Android only; desktop video hosts deliberately ignore them. */
data class LiveVideoGestures(
    val onTap: () -> Unit,
    val onNextChannel: () -> Unit,
    val onPreviousChannel: () -> Unit
)

internal enum class LiveTouchAction { TAP, NEXT_CHANNEL, PREVIOUS_CHANNEL, NONE }

internal fun classifyLiveTouch(
    deltaX: Float,
    deltaY: Float,
    durationMillis: Long,
    thresholdPx: Float,
    maxTapDurationMillis: Long
): LiveTouchAction = when {
    kotlin.math.abs(deltaY) >= thresholdPx && kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) ->
        if (deltaY < 0f) LiveTouchAction.NEXT_CHANNEL else LiveTouchAction.PREVIOUS_CHANNEL
    kotlin.math.abs(deltaX) < thresholdPx && kotlin.math.abs(deltaY) < thresholdPx && durationMillis <= maxTapDurationMillis ->
        LiveTouchAction.TAP
    else -> LiveTouchAction.NONE
}

enum class PlaybackState {
    IDLE, OPENING, BUFFERING, PLAYING, RECONNECTING, ERROR
}

/** Data rendered above the platform video surface. */
data class PlaybackOverlayData(
    val channelId: String,
    val channelNumber: String,
    val channelName: String,
    val logoUrl: String?,
    val showProgrammeInfo: Boolean,
    val showPreviewLogo: Boolean,
    val channelNumberInput: String?,
    val noEpgLabel: String,
    val nextLabel: String,
    val currentTitle: String?,
    val currentStart: Long?,
    val currentEnd: Long?,
    val remainingText: String?,
    val nextTitle: String?,
    val nextStart: Long?,
    val nextEnd: Long?,
    val now: Long,
    val playbackStatus: String? = null,
    val playbackError: Boolean = false,
    val showBufferingSpinner: Boolean = false,
    val bufferingLabel: String? = null
)
