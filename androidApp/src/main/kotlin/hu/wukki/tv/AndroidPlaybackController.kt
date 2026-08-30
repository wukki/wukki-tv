package hu.wukki.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.tr

/**
 * Android Media3 implementation of the app-level playback contract. It owns one player instance
 * while the app is open, so audio keeps running when the user moves between Wukki screens.
 */
class AndroidPlaybackController(private val context: Context) : PlaybackEngine {
    private val handler = Handler(Looper.getMainLooper())
    // The player is created after the first composition. Keeping it as Compose state guarantees
    // that the AndroidView receives the video surface even when no parent UI parameter changed.
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var currentChannel: Channel? = null
    private var currentSettings = PlaybackSettings()
    private var currentShowLogos = true
    private var currentLanguage = AppLanguage.HUNGARIAN
    private var reconnectAttempt = 0
    private var released = false
    private var resumeAfterBackground = false
    private var reconnectRunnable: Runnable? = null
    private var bufferingRunnable: Runnable? = null

    override var state by mutableStateOf(PlaybackState.IDLE)
        private set
    override var detail by mutableStateOf<String?>(null)
        private set
    override var successfullyPlayedChannelId by mutableStateOf<String?>(null)
        private set
    var overlay by mutableStateOf<PlaybackOverlayData?>(null)
        private set
    private var surfaceAspectRatio by mutableStateOf(AspectRatioMode.AUTO)

    override fun play(channel: Channel?, settings: PlaybackSettings, showLogos: Boolean, language: AppLanguage) {
        if (released || channel == null) return
        resumeAfterBackground = false
        val sourceChanged = currentChannel?.streamUrl != channel.streamUrl
        val bufferChanged = currentSettings.bufferProfile != settings.bufferProfile
        currentChannel = channel
        currentSettings = settings
        currentShowLogos = showLogos
        currentLanguage = language
        surfaceAspectRatio = settings.aspectRatio ?: AspectRatioMode.AUTO
        if (player == null || bufferChanged) createPlayer()
        player?.volume = settings.volume.coerceIn(0, 100) / 100f
        if (sourceChanged || bufferChanged || state == PlaybackState.IDLE || state == PlaybackState.ERROR) {
            reconnectAttempt = 0
            cancelPendingCallbacks()
            startCurrentChannel()
        }
    }

    override fun updateSettings(settings: PlaybackSettings) {
        val channel = currentChannel ?: return
        play(channel, settings, currentShowLogos, currentLanguage)
    }

    override fun updateOverlay(data: PlaybackOverlayData) {
        overlay = data
    }

    override fun stop() {
        resumeAfterBackground = false
        cancelPendingCallbacks()
        player?.stop()
        updateState(PlaybackState.IDLE, null)
    }

    /** Stops active playback while the activity is not visible, without losing its channel context. */
    fun pauseForBackground() {
        if (released) return
        resumeAfterBackground = currentChannel != null && state in setOf(
            PlaybackState.OPENING,
            PlaybackState.BUFFERING,
            PlaybackState.PLAYING,
            PlaybackState.RECONNECTING
        )
        if (!resumeAfterBackground) return
        cancelPendingCallbacks()
        player?.stop()
        updateState(PlaybackState.IDLE, null)
    }

    /** Restarts the previously active stream after the activity returns to the foreground. */
    fun resumeAfterBackground() {
        if (released || !resumeAfterBackground) return
        resumeAfterBackground = false
        if (player == null) createPlayer()
        reconnectAttempt = 0
        cancelPendingCallbacks()
        startCurrentChannel()
    }

    override fun release() {
        if (released) return
        released = true
        resumeAfterBackground = false
        cancelPendingCallbacks()
        player?.release()
        player = null
        overlay = null
    }

    @Composable
    fun VideoSurface(modifier: Modifier = Modifier, gestures: LiveVideoGestures? = null) {
        val activePlayer = player
        val ratio = surfaceAspectRatio
        BoxWithConstraints(modifier = modifier.background(WukkiColors.video)) {
            val targetAspect = ratio.targetAspectRatio()
            val hostAspect = if (maxHeight.value == 0f) 1f else maxWidth.value / maxHeight.value
            val playerModifier = targetAspect?.let { target ->
                if (hostAspect > target) Modifier.fillMaxHeight().aspectRatio(target)
                else Modifier.fillMaxWidth().aspectRatio(target)
            } ?: Modifier.fillMaxSize()
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        player = activePlayer
                        resizeMode = ratio.resizeMode()
                        installLiveTouchHandler(this, gestures)
                    }
                },
                update = { view ->
                    if (view.player !== activePlayer) view.player = activePlayer
                    view.resizeMode = ratio.resizeMode()
                    installLiveTouchHandler(view, gestures)
                },
                modifier = playerModifier.align(Alignment.Center)
            )
            AndroidPlaybackOverlay(overlay, Modifier.fillMaxSize())
        }
    }

    private fun installLiveTouchHandler(view: PlayerView, gestures: LiveVideoGestures?) {
        if (gestures == null) {
            view.setOnTouchListener(null)
            return
        }
        val thresholdPx = LIVE_SWIPE_THRESHOLD_DP * view.resources.displayMetrics.density
        var downX = 0f
        var downY = 0f
        var downAt = 0L
        var singlePointer = true
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downAt = event.eventTime
                    singlePointer = true
                }
                MotionEvent.ACTION_POINTER_DOWN -> singlePointer = false
                MotionEvent.ACTION_UP -> if (singlePointer) {
                    when (classifyLiveTouch(event.x - downX, event.y - downY, event.eventTime - downAt, thresholdPx, MAX_TAP_DURATION_MS)) {
                        LiveTouchAction.TAP -> gestures.onTap()
                        LiveTouchAction.NEXT_CHANNEL -> gestures.onNextChannel()
                        LiveTouchAction.PREVIOUS_CHANNEL -> gestures.onPreviousChannel()
                        LiveTouchAction.NONE -> Unit
                    }
                }
                MotionEvent.ACTION_CANCEL -> singlePointer = false
            }
            true
        }
    }

    private fun createPlayer() {
        player?.release()
        val buffers = currentSettings.bufferProfile.bufferDurations()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(buffers.minBufferMs, buffers.maxBufferMs, buffers.playbackMs, buffers.rebufferMs)
            .build()
        player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .also { mediaPlayer ->
                mediaPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> scheduleBufferingIndicator()
                            Player.STATE_READY -> {
                                cancelBufferingIndicator()
                                if (mediaPlayer.playWhenReady) onPlaying()
                            }
                            Player.STATE_ENDED -> onPlaybackFailure(null)
                            Player.STATE_IDLE -> Unit
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) = onPlaybackFailure(error)
                })
            }
    }

    private fun startCurrentChannel() {
        val channel = currentChannel ?: return
        val mediaPlayer = player ?: return
        updateState(PlaybackState.OPENING, tr(currentLanguage, "playback.channel.opening", channel.name))
        mediaPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(channel.streamUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        )
        mediaPlayer.prepare()
        mediaPlayer.playWhenReady = true
    }

    private fun onPlaying() {
        reconnectAttempt = 0
        reconnectRunnable?.let(handler::removeCallbacks)
        reconnectRunnable = null
        successfullyPlayedChannelId = currentChannel?.id
        updateState(PlaybackState.PLAYING, null)
    }

    private fun onPlaybackFailure(error: PlaybackException?) {
        if (released) return
        cancelBufferingIndicator()
        val channel = currentChannel ?: return
        val reason = error?.errorCodeName ?: detail
        if (!currentSettings.autoReconnect || reconnectAttempt >= currentSettings.reconnectAttempts) {
            updateState(PlaybackState.ERROR, tr(currentLanguage, "playback.stream.failed", channel.name, reason ?: tr(currentLanguage, "error.unknown")))
            return
        }
        reconnectAttempt++
        updateState(
            PlaybackState.RECONNECTING,
            tr(currentLanguage, "playback.reconnect.attempt", channel.name, reconnectAttempt, currentSettings.reconnectAttempts)
        )
        reconnectRunnable?.let(handler::removeCallbacks)
        reconnectRunnable = Runnable { if (!released) startCurrentChannel() }.also { handler.postDelayed(it, RECONNECT_DELAY_MS) }
    }

    private fun scheduleBufferingIndicator() {
        if (bufferingRunnable != null || state == PlaybackState.BUFFERING || released) return
        bufferingRunnable = Runnable {
            bufferingRunnable = null
            if (!released && player?.playbackState == Player.STATE_BUFFERING) updateState(PlaybackState.BUFFERING, null)
        }.also { handler.postDelayed(it, BUFFERING_VISIBILITY_DELAY_MS) }
    }

    private fun cancelBufferingIndicator() {
        bufferingRunnable?.let(handler::removeCallbacks)
        bufferingRunnable = null
    }

    private fun cancelPendingCallbacks() {
        reconnectRunnable?.let(handler::removeCallbacks)
        reconnectRunnable = null
        cancelBufferingIndicator()
    }

    private fun updateState(value: PlaybackState, message: String?) {
        state = value
        detail = message
    }

    private fun BufferProfile.bufferDurations() = when (this) {
        BufferProfile.LOW_LATENCY -> BufferDurations(1_000, 6_000, 500, 1_000)
        BufferProfile.BALANCED -> BufferDurations(3_000, 15_000, 1_000, 2_000)
        BufferProfile.STABLE -> BufferDurations(8_000, 30_000, 2_000, 4_000)
    }

    private fun AspectRatioMode.resizeMode() = when (this) {
        AspectRatioMode.FILL_CROP, AspectRatioMode.RATIO_16_9, AspectRatioMode.RATIO_4_3, AspectRatioMode.RATIO_21_9 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        AspectRatioMode.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    private fun AspectRatioMode.targetAspectRatio(): Float? = when (this) {
        AspectRatioMode.RATIO_16_9 -> 16f / 9f
        AspectRatioMode.RATIO_4_3 -> 4f / 3f
        AspectRatioMode.RATIO_21_9 -> 21f / 9f
        AspectRatioMode.AUTO, AspectRatioMode.FILL_CROP -> null
    }

    private data class BufferDurations(val minBufferMs: Int, val maxBufferMs: Int, val playbackMs: Int, val rebufferMs: Int)

    private companion object {
        const val BUFFERING_VISIBILITY_DELAY_MS = 250L
        const val RECONNECT_DELAY_MS = 1_000L
        const val LIVE_SWIPE_THRESHOLD_DP = 48f
        const val MAX_TAP_DURATION_MS = 300L
    }
}
