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
    val onPreviousChannel: () -> Unit,
    val onShowNavigation: () -> Unit
)

enum class LiveTouchAction { TAP, NEXT_CHANNEL, PREVIOUS_CHANNEL, SHOW_NAVIGATION, NONE }

fun classifyLiveTouch(
    deltaX: Float,
    deltaY: Float,
    durationMillis: Long,
    thresholdPx: Float,
    maxTapDurationMillis: Long,
    startedInTopEdge: Boolean = false
): LiveTouchAction = when {
    startedInTopEdge && deltaY >= thresholdPx && kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) ->
        LiveTouchAction.SHOW_NAVIGATION
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
    val programmeImageUrl: String?,
    val showProgrammeInfo: Boolean,
    val showPreviewLogo: Boolean,
    val channelNumberInput: String?,
    val noEpgLabel: String,
    val nextLabel: String,
    val currentTitle: String?,
    val currentStart: Long?,
    val currentEnd: Long?,
    val nextTitle: String?,
    val now: Long,
    val playbackStatus: String? = null,
    val playbackError: Boolean = false,
    val showBufferingSpinner: Boolean = false,
    val bufferingLabel: String? = null
)
