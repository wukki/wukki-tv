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

/**
 * Android Media3 implementation of the app-level playback contract. It owns one player instance
 * while the app is open, so audio keeps running when the user moves between Wukki screens.
 */
class AndroidPlaybackController(private val context: Context) : PlaybackEngine {
    private val handler = Handler(Looper.getMainLooper())
    // The player is created after the first composition. Keeping it as Compose state guarantees
    // that the AndroidView receives the video surface even when no parent UI parameter changed.
    private var player by mutableStateOf<ExoPlayer?>(null)
    private val session: PlaybackSession = PlaybackSession(object : PlaybackAdapter {
        override fun play(channel: Channel, buffers: PlaybackBufferPolicy, generation: Long) {
            createPlayer(buffers, generation)
            player?.setMediaItem(MediaItem.Builder().setUri(channel.streamUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8).build())
            player?.prepare()
            player?.playWhenReady = true
        }
        override fun stop() { player?.stop() }
        override fun volume(value: Int) { player?.volume = value / 100f }
        override fun aspect(value: AspectRatioMode) { surfaceAspectRatio = value }
    }, PlaybackScheduler { delay, action ->
        val task = Runnable(action)
        handler.postDelayed(task, delay)
        PlaybackCancellation { handler.removeCallbacks(task) }
    })
    override val state get() = session.state
    override val detail get() = session.detail
    override val successfullyPlayedChannelId get() = session.successfullyPlayedChannelId
    var overlay by mutableStateOf<PlaybackOverlayData?>(null)
        private set
    private var surfaceAspectRatio by mutableStateOf(AspectRatioMode.AUTO)

    override fun play(channel: Channel?, settings: PlaybackSettings, showLogos: Boolean, language: AppLanguage) =
        session.play(channel, settings, language)
    override fun updateSettings(settings: PlaybackSettings) = session.updateSettings(settings)
    override fun updateOverlay(data: PlaybackOverlayData) { overlay = data }
    override fun stop() = session.stop()
    fun pauseForBackground() = session.pauseForBackground()
    fun resumeAfterBackground() = session.resumeAfterBackground()
    override fun release() {
        session.release()
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
                    val startedInTopEdge = downY <= LIVE_NAVIGATION_EDGE_DP * view.resources.displayMetrics.density
                    when (classifyLiveTouch(
                        deltaX = event.x - downX,
                        deltaY = event.y - downY,
                        durationMillis = event.eventTime - downAt,
                        thresholdPx = thresholdPx,
                        maxTapDurationMillis = MAX_TAP_DURATION_MS,
                        startedInTopEdge = startedInTopEdge
                    )) {
                        LiveTouchAction.TAP -> gestures.onTap()
                        LiveTouchAction.NEXT_CHANNEL -> gestures.onNextChannel()
                        LiveTouchAction.PREVIOUS_CHANNEL -> gestures.onPreviousChannel()
                        LiveTouchAction.SHOW_NAVIGATION -> gestures.onShowNavigation()
                        LiveTouchAction.NONE -> Unit
                    }
                }
                MotionEvent.ACTION_CANCEL -> singlePointer = false
            }
            true
        }
    }

    private fun createPlayer(buffers: PlaybackBufferPolicy, generation: Long) {
        player?.release()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(buffers.minBufferMs, buffers.maxBufferMs, buffers.playbackMs, buffers.rebufferMs)
            .build()
        player = ExoPlayer.Builder(context).setLoadControl(loadControl).build().also { mediaPlayer ->
            mediaPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> session.bufferingStarted(generation)
                        Player.STATE_READY -> playbackReady(generation, mediaPlayer.playWhenReady)
                        Player.STATE_ENDED -> session.failed(generation)
                    }
                }
                override fun onPlayerError(error: PlaybackException) = session.failed(generation, error.errorCodeName)
            })
        }
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

    private fun playbackReady(
        generation: Long,
        playWhenReady: Boolean,
    ) {
        session.bufferingEnded(generation)
        if (playWhenReady) session.playing(generation)
    }

    private companion object {
        const val LIVE_SWIPE_THRESHOLD_DP = 48f
        const val LIVE_NAVIGATION_EDGE_DP = 48f
        const val MAX_TAP_DURATION_MS = 300L
    }
}
