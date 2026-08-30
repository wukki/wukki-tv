package hu.wukki.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hu.wukki.tv.ui.components.tr
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * Owns one libVLC instance for the full lifetime of the Compose application.
 * The Swing host may be removed while browsing other screens; audio and the stream keep running.
 */
class PlaybackController(initialLanguage: AppLanguage = AppLanguage.HUNGARIAN) : PlaybackEngine {
    private val retryExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "wukki-vlc-reconnect").apply { isDaemon = true }
    }
    private var retryTask: ScheduledFuture<*>? = null
    private var bufferingTask: ScheduledFuture<*>? = null
    private var spinnerRepaintTask: ScheduledFuture<*>? = null
    private var currentChannel: Channel? = null
    private var currentSettings: PlaybackSettings = PlaybackSettings()
    private var currentShowLogos = true
    private var currentLanguage = initialLanguage
    private var attempt = 0
    private var released = false

    override var state by mutableStateOf(PlaybackState.IDLE)
        private set
    override var detail by mutableStateOf<String?>(null)
        private set
    /** Set from libVLC's `playing` event; consumed by the Compose application layer. */
    override var successfullyPlayedChannelId by mutableStateOf<String?>(null)
        private set

    private val runtimeResolution = VlcRuntimeResolver.resolve()
    private val runtime = runtimeResolution.runtime
    /**
     * Callback rendering avoids the macOS native-window requirement of VLC's embedded vout.
     * It is also reliable when Compose re-parents the Swing component between screens.
     */
    private val overlayComponent: OverlayCallbackMediaPlayerComponent? = createComponent()
    private val overlayCoordinator = overlayComponent?.let {
        DesktopPlaybackOverlayCoordinator(it, ::requestRepaint)
    }
    val component: CallbackMediaPlayerComponent? get() = overlayComponent

    init {
        component?.mediaPlayer()?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun opening(mediaPlayer: MediaPlayer) = updateState(PlaybackState.OPENING, null)

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                if (newCache < 100f) scheduleBufferingIndicator()
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                attempt = 0
                retryTask?.cancel(false)
                retryTask = null
                cancelBufferingIndicator()
                successfullyPlayedChannelId = currentChannel?.id
                updateState(PlaybackState.PLAYING, null)
            }

            override fun error(mediaPlayer: MediaPlayer) = onPlaybackFailure()
            override fun finished(mediaPlayer: MediaPlayer) = onPlaybackFailure()
        })
    }

    override fun play(channel: Channel?, settings: PlaybackSettings, showLogos: Boolean, language: AppLanguage) {
        if (channel == null || released) return
        val changedChannel = currentChannel?.streamUrl != channel.streamUrl
        val changedBuffer = currentSettings.bufferProfile != settings.bufferProfile
        currentChannel = channel
        currentSettings = settings
        currentShowLogos = showLogos
        currentLanguage = language
        applyAspectRatio(settings.aspectRatio ?: AspectRatioMode.AUTO)
        component?.mediaPlayer()?.audio()?.setVolume(settings.volume)

        if (changedChannel || changedBuffer || state == PlaybackState.IDLE || state == PlaybackState.ERROR) {
            attempt = 0
            retryTask?.cancel(false)
            retryTask = null
            cancelBufferingIndicator()
            startCurrentChannel()
        }
    }

    override fun updateSettings(settings: PlaybackSettings) {
        val channel = currentChannel ?: return
        play(channel, settings, currentShowLogos, currentLanguage)
    }

    /** Updates the Java2D video overlay without restarting or reconfiguring the stream. */
    override fun updateOverlay(data: PlaybackOverlayData) {
        if (released) return
        overlayCoordinator?.update(data)
    }

    override fun stop() {
        retryTask?.cancel(false)
        retryTask = null
        cancelBufferingIndicator()
        component?.mediaPlayer()?.controls()?.stop()
        updateState(PlaybackState.IDLE, null)
    }

    override fun release() {
        if (released) return
        released = true
        retryTask?.cancel(true)
        bufferingTask?.cancel(true)
        retryExecutor.shutdownNow()
        overlayCoordinator?.release()
        runCatching { component?.release() }
    }

    private fun createComponent(): OverlayCallbackMediaPlayerComponent? = try {
        if (runtime == null && !NativeDiscovery().discover()) {
            val messageKey = when (runtimeResolution.issue) {
                VlcRuntimeIssue.VIDEO_PLUGIN_MISSING -> "playback.runtime.video.plugin.missing"
                VlcRuntimeIssue.MISSING -> "playback.runtime.missing"
            }
            updateState(PlaybackState.ERROR, tr(currentLanguage, messageKey))
            null
        } else {
            OverlayCallbackMediaPlayerComponent(*runtime?.factoryArguments.orEmpty())
        }
    } catch (exception: Exception) {
        updateState(PlaybackState.ERROR, tr(currentLanguage, "playback.runtime.initialization", exception.message ?: tr(currentLanguage, "error.unknown")))
        null
    }

    private fun startCurrentChannel() {
        val channel = currentChannel ?: return
        val player = component?.mediaPlayer()
        if (player == null) {
            updateState(PlaybackState.ERROR, detail ?: tr(currentLanguage, "playback.player.unavailable"))
            return
        }
        try {
            cancelBufferingIndicator()
            player.controls().stop()
            player.audio().setVolume(currentSettings.volume)
            updateState(PlaybackState.OPENING, tr(currentLanguage, "playback.channel.opening", channel.name))
            player.media().play(channel.streamUrl, currentSettings.bufferProfile.vlcOption(), ":http-reconnect")
        } catch (exception: Exception) {
            onPlaybackFailure(exception.message)
        }
    }

    /** Changes only how already-decoded frames are painted, so the stream keeps playing. */
    private fun applyAspectRatio(mode: AspectRatioMode) {
        component?.setImagePainter(AspectRatioImagePainter(mode))
        requestRepaint()
    }

    private fun requestRepaint() {
        SwingUtilities.invokeLater { component?.videoSurfaceComponent()?.repaint() }
    }

    private fun onPlaybackFailure(reason: String? = null) {
        val channel = currentChannel ?: return
        if (released || retryTask != null) return
        cancelBufferingIndicator()
        val nextAttempt = attempt + 1
        if (!currentSettings.autoReconnect || nextAttempt > currentSettings.reconnectAttempts) {
            updateState(PlaybackState.ERROR, tr(currentLanguage, "playback.stream.failed", channel.name, reason ?: tr(currentLanguage, "error.unknown")))
            return
        }
        attempt = nextAttempt
        updateState(PlaybackState.RECONNECTING, tr(currentLanguage, "playback.reconnect.attempt", channel.name, attempt, currentSettings.reconnectAttempts))
        retryTask = retryExecutor.schedule({
            retryTask = null
            startCurrentChannel()
        }, attempt.toLong(), TimeUnit.SECONDS)
    }

    private fun updateState(newState: PlaybackState, newDetail: String?) {
        state = newState
        detail = newDetail
    }

    private fun scheduleBufferingIndicator() {
        if (state == PlaybackState.BUFFERING || bufferingTask != null || released) return
        bufferingTask = retryExecutor.schedule({
            bufferingTask = null
            if (!released && state != PlaybackState.PLAYING) {
                updateState(PlaybackState.BUFFERING, null)
                startSpinnerRepaintLoop()
            }
        }, BUFFERING_INDICATOR_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun cancelBufferingIndicator() {
        bufferingTask?.cancel(false)
        bufferingTask = null
        spinnerRepaintTask?.cancel(false)
        spinnerRepaintTask = null
    }

    private fun startSpinnerRepaintLoop() {
        if (spinnerRepaintTask != null) return
        spinnerRepaintTask = retryExecutor.scheduleAtFixedRate(
            { if (!released && state == PlaybackState.BUFFERING) requestRepaint() },
            0L,
            SPINNER_FRAME_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private companion object {
        const val BUFFERING_INDICATOR_DELAY_MS = 250L
        const val SPINNER_FRAME_INTERVAL_MS = 33L
    }
}
