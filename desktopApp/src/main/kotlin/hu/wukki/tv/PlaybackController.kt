package hu.wukki.tv

import hu.wukki.tv.ui.components.tr
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import javax.swing.SwingUtilities

/**
 * Owns one libVLC instance for the full lifetime of the Compose application.
 * The Swing host may be removed while browsing other screens; audio and the stream keep running.
 */
class PlaybackController(initialLanguage: AppLanguage = AppLanguage.HUNGARIAN) : PlaybackEngine {
    private var runtimeError: String? = null
    private var released = false
    private var listener: MediaPlayerEventAdapter? = null
    private val session: PlaybackSession = PlaybackSession(object : PlaybackAdapter {
        override fun play(channel: Channel, buffers: PlaybackBufferPolicy, generation: Long) {
            val native = component?.mediaPlayer() ?: error(runtimeError ?: "Player unavailable")
            listener?.let { native.events().removeMediaPlayerEventListener(it) }
            listener = object : MediaPlayerEventAdapter() {
                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    if (newCache < 100f) SwingUtilities.invokeLater { session.buffering(generation) }
                }
                override fun playing(mediaPlayer: MediaPlayer) { SwingUtilities.invokeLater { session.playing(generation) } }
                override fun error(mediaPlayer: MediaPlayer) { SwingUtilities.invokeLater { session.failed(generation) } }
                override fun finished(mediaPlayer: MediaPlayer) { SwingUtilities.invokeLater { session.failed(generation) } }
            }.also { native.events().addMediaPlayerEventListener(it) }
            native.media().play(channel.streamUrl, ":network-caching=${buffers.networkCacheMs}")
        }
        override fun stop() {
            val native = component?.mediaPlayer() ?: return
            listener?.let { native.events().removeMediaPlayerEventListener(it) }
            listener = null
            native.controls().stop()
        }
        override fun volume(value: Int) { component?.mediaPlayer()?.audio()?.setVolume(value) }
        override fun aspect(value: AspectRatioMode) { applyAspectRatio(value) }
    }, PlaybackScheduler { delay, action ->
        val timer = javax.swing.Timer(delay.toInt()) { action() }.apply { isRepeats = false; start() }
        PlaybackCancellation { timer.stop() }
    })
    override val state get() = session.state
    override val detail get() = session.detail ?: runtimeError
    override val successfullyPlayedChannelId get() = session.successfullyPlayedChannelId
    private val spinnerTimer = javax.swing.Timer(33) {
        if (state == PlaybackState.BUFFERING) requestRepaint()
    }.apply { start() }
    private var currentLanguage = initialLanguage

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

    override fun play(channel: Channel?, settings: PlaybackSettings, showLogos: Boolean, language: AppLanguage) {
        currentLanguage = language
        session.play(channel, settings, language)
    }
    override fun updateSettings(settings: PlaybackSettings) = session.updateSettings(settings)
    override fun updateOverlay(data: PlaybackOverlayData) {
        if (!released) overlayCoordinator?.update(data)
    }
    override fun stop() = session.stop()
    override fun release() {
        if (released) return
        session.release()
        released = true
        spinnerTimer.stop()
        overlayCoordinator?.release()
        runCatching { component?.release() }
    }

    private fun createComponent(): OverlayCallbackMediaPlayerComponent? = try {
        if (runtime == null && !NativeDiscovery().discover()) {
            val messageKey = when (runtimeResolution.issue) {
                VlcRuntimeIssue.VIDEO_PLUGIN_MISSING -> "playback.runtime.video.plugin.missing"
                VlcRuntimeIssue.MISSING -> "playback.runtime.missing"
            }
            runtimeError = tr(currentLanguage, messageKey)
            null
        } else {
            OverlayCallbackMediaPlayerComponent(*runtime?.factoryArguments.orEmpty())
        }
    } catch (exception: Exception) {
        runtimeError = tr(currentLanguage, "playback.runtime.initialization", exception.message ?: tr(currentLanguage, "error.unknown"))
        null
    }

    /** Changes only how already-decoded frames are painted, so the stream keeps playing. */
    private fun applyAspectRatio(mode: AspectRatioMode) {
        component?.setImagePainter(AspectRatioImagePainter(mode))
        requestRepaint()
    }

    private fun requestRepaint() {
        SwingUtilities.invokeLater { component?.videoSurfaceComponent()?.repaint() }
    }

}
