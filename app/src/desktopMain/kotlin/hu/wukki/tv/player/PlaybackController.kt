package hu.wukki.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.callback.CallbackImagePainter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.imageio.ImageIO
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class PlaybackState {
    IDLE, OPENING, BUFFERING, PLAYING, RECONNECTING, ERROR
}

/**
 * Owns one libVLC instance for the full lifetime of the Compose application.
 * The Swing host may be removed while browsing other screens; audio and the stream keep running.
 */
class PlaybackController {
    private val retryExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "wukki-vlc-reconnect").apply { isDaemon = true }
    }
    private val logoExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wukki-vlc-logo").apply { isDaemon = true }
    }
    private var retryTask: ScheduledFuture<*>? = null
    private var currentChannel: Channel? = null
    private var currentSettings: PlaybackSettings = PlaybackSettings()
    private var currentShowLogos = true
    private var attempt = 0
    private var released = false

    var state by mutableStateOf(PlaybackState.IDLE)
        private set
    var detail by mutableStateOf<String?>(null)
        private set

    private val runtime = VlcRuntimeResolver.find()
    /**
     * Callback rendering avoids the macOS native-window requirement of VLC's embedded vout.
     * It is also reliable when Compose re-parents the Swing component between screens.
     */
    private val overlayComponent: OverlayCallbackMediaPlayerComponent? = createComponent()
    val component: CallbackMediaPlayerComponent? get() = overlayComponent

    init {
        component?.mediaPlayer()?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun opening(mediaPlayer: MediaPlayer) = updateState(PlaybackState.OPENING, null)

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                if (newCache < 100f) updateState(PlaybackState.BUFFERING, "Pufferelés: ${newCache.toInt()}%")
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                attempt = 0
                retryTask?.cancel(false)
                retryTask = null
                updateState(PlaybackState.PLAYING, null)
            }

            override fun error(mediaPlayer: MediaPlayer) = onPlaybackFailure()
            override fun finished(mediaPlayer: MediaPlayer) = onPlaybackFailure()
        })
    }

    fun play(channel: Channel?, settings: PlaybackSettings, showLogos: Boolean = true) {
        if (channel == null || released) return
        val changedChannel = currentChannel?.streamUrl != channel.streamUrl
        val changedLogoVisibility = currentShowLogos != showLogos
        val changedBuffer = currentSettings.bufferProfile != settings.bufferProfile
        currentChannel = channel
        currentSettings = settings
        currentShowLogos = showLogos
        applyAspectRatio(settings.aspectRatio ?: AspectRatioMode.AUTO)
        if (changedChannel || changedLogoVisibility || overlayComponent?.overlay?.channelName != channel.name) updateOverlay(channel, showLogos)
        component?.mediaPlayer()?.audio()?.setVolume(settings.volume)

        if (changedChannel || changedBuffer || state == PlaybackState.IDLE || state == PlaybackState.ERROR) {
            attempt = 0
            retryTask?.cancel(false)
            retryTask = null
            startCurrentChannel()
        }
    }

    fun updateSettings(settings: PlaybackSettings) {
        val channel = currentChannel ?: return
        play(channel, settings)
    }

    fun stop() {
        retryTask?.cancel(false)
        retryTask = null
        component?.mediaPlayer()?.controls()?.stop()
        updateState(PlaybackState.IDLE, null)
    }

    fun release() {
        if (released) return
        released = true
        retryTask?.cancel(true)
        retryExecutor.shutdownNow()
        logoExecutor.shutdownNow()
        runCatching { component?.release() }
    }

    private fun createComponent(): OverlayCallbackMediaPlayerComponent? = try {
        if (runtime == null && !NativeDiscovery().discover()) {
            updateState(PlaybackState.ERROR, "A beágyazott VLC runtime nem található. Telepíts VLC-t, vagy használj a VLC runtime-ot tartalmazó alkalmazáscsomagot.")
            null
        } else {
            OverlayCallbackMediaPlayerComponent(*runtime?.factoryArguments.orEmpty())
        }
    } catch (exception: Exception) {
        updateState(PlaybackState.ERROR, "A VLC inicializálása sikertelen: ${exception.message ?: "ismeretlen hiba"}")
        null
    }

    private fun startCurrentChannel() {
        val channel = currentChannel ?: return
        val player = component?.mediaPlayer()
        if (player == null) {
            updateState(PlaybackState.ERROR, detail ?: "A VLC lejátszó nem indítható.")
            return
        }
        try {
            player.controls().stop()
            player.audio().setVolume(currentSettings.volume)
            updateState(PlaybackState.OPENING, "${channel.name} betöltése…")
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

    private fun updateOverlay(channel: Channel, showLogos: Boolean) {
        val overlay = PlaybackOverlay(channel.name, showLogos)
        overlayComponent?.overlay = overlay
        requestRepaint()
        val logoUrl = channel.logo?.takeIf { showLogos } ?: return
        logoExecutor.execute {
            val logo = runCatching {
                URL(logoUrl).openConnection().apply { connectTimeout = 10_000; readTimeout = 15_000 }.getInputStream().use(ImageIO::read)
            }.getOrNull()
            if (!released && currentChannel?.id == channel.id && currentShowLogos) {
                overlayComponent?.overlay = overlay.copy(logo = logo)
                requestRepaint()
            }
        }
    }

    private fun requestRepaint() {
        SwingUtilities.invokeLater { component?.videoSurfaceComponent()?.repaint() }
    }

    private fun onPlaybackFailure(reason: String? = null) {
        val channel = currentChannel ?: return
        if (released || retryTask != null) return
        val nextAttempt = attempt + 1
        if (!currentSettings.autoReconnect || nextAttempt > currentSettings.reconnectAttempts) {
            updateState(PlaybackState.ERROR, "${channel.name} nem indítható${reason?.let { ": $it" } ?: ". Ellenőrizd a stream URL-t vagy a hálózatot."}")
            return
        }
        attempt = nextAttempt
        updateState(PlaybackState.RECONNECTING, "${channel.name} újracsatlakoztatása ($attempt/${currentSettings.reconnectAttempts})…")
        retryTask = retryExecutor.schedule({
            retryTask = null
            startCurrentChannel()
        }, attempt.toLong(), TimeUnit.SECONDS)
    }

    private fun updateState(newState: PlaybackState, newDetail: String?) {
        state = newState
        detail = newDetail
    }
}

private data class PlaybackOverlay(val channelName: String, val showLogo: Boolean, val logo: BufferedImage? = null)

/** The overlay is painted by the same Swing component as the callback video, above every frame. */
private class OverlayCallbackMediaPlayerComponent(vararg factoryArguments: String) : CallbackMediaPlayerComponent(*factoryArguments) {
    @Volatile var overlay: PlaybackOverlay? = null

    override fun onPaintOverlay(graphics: Graphics2D) {
        super.onPaintOverlay(graphics)
        val content = overlay ?: return
        val surface = videoSurfaceComponent()
        val width = surface.width
        val height = surface.height
        if (width <= 0 || height <= 0) return

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

}

/** Draws callback video frames without distortion and crops symmetrically when requested. */
private class AspectRatioImagePainter(private val mode: AspectRatioMode) : CallbackImagePainter {
    override fun prepare(graphics: Graphics2D, component: JComponent) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    override fun paint(graphics: Graphics2D, component: JComponent, image: BufferedImage?) {
        val surfaceWidth = component.width
        val surfaceHeight = component.height
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        graphics.color = component.background ?: Color.BLACK
        graphics.fillRect(0, 0, surfaceWidth, surfaceHeight)
        image ?: return

        val target = targetBounds(surfaceWidth, surfaceHeight)
        val scale = if (mode == AspectRatioMode.AUTO) {
            min(target.width / image.width.toDouble(), target.height / image.height.toDouble())
        } else {
            max(target.width / image.width.toDouble(), target.height / image.height.toDouble())
        }
        val drawnWidth = image.width * scale
        val drawnHeight = image.height * scale
        val x = target.x + (target.width - drawnWidth) / 2
        val y = target.y + (target.height - drawnHeight) / 2
        val previousClip = graphics.clip
        graphics.clipRect(target.x.toInt(), target.y.toInt(), target.width.toInt(), target.height.toInt())
        graphics.drawImage(image, x.toInt(), y.toInt(), drawnWidth.toInt(), drawnHeight.toInt(), null)
        graphics.clip = previousClip
    }

    private fun targetBounds(width: Int, height: Int): DrawBounds {
        val ratio = mode.targetRatio ?: return DrawBounds(0.0, 0.0, width.toDouble(), height.toDouble())
        val surfaceRatio = width.toDouble() / height
        return if (surfaceRatio > ratio) {
            val targetWidth = height * ratio
            DrawBounds((width - targetWidth) / 2, 0.0, targetWidth, height.toDouble())
        } else {
            val targetHeight = width / ratio
            DrawBounds(0.0, (height - targetHeight) / 2, width.toDouble(), targetHeight)
        }
    }
}

private data class DrawBounds(val x: Double, val y: Double, val width: Double, val height: Double)

private val AspectRatioMode.targetRatio: Double?
    get() = when (this) {
        AspectRatioMode.AUTO, AspectRatioMode.FILL_CROP -> null
        AspectRatioMode.RATIO_16_9 -> 16.0 / 9.0
        AspectRatioMode.RATIO_4_3 -> 4.0 / 3.0
        AspectRatioMode.RATIO_21_9 -> 21.0 / 9.0
    }

@Composable
fun EmbeddedVlcPlayer(controller: PlaybackController, modifier: Modifier = Modifier) {
    controller.component?.let { component ->
        SwingPanel(factory = { component }, modifier = modifier)
    }
}

private data class VlcRuntime(val home: File, val pluginDirectory: File?) {
    val factoryArguments: Array<String>
        get() = buildList {
            add("--no-video-title-show")
            add("--quiet")
        }.toTypedArray()
}

/** Finds a packaged runtime first, then a developer's local VLC installation. */
private object VlcRuntimeResolver {
    fun find(): VlcRuntime? {
        val configured = sequenceOf(
            System.getProperty("wukki.vlc.home"),
            System.getenv("WUKKI_VLC_HOME"),
            File(System.getProperty("user.dir"), "runtime/vlc").absolutePath
        ).filterNotNull().map(::File)

        val packaged = sequenceOf(
            configured,
            packagedCandidates().asSequence(),
            systemCandidates().asSequence()
        ).flatten().firstOrNull(::isRuntime)
            ?: return null

        configureNativePath(packaged)
        return VlcRuntime(packaged, pluginDirectory(packaged))
    }

    private fun packagedCandidates(): List<File> {
        val codeSource = runCatching {
            File(PlaybackController::class.java.protectionDomain.codeSource.location.toURI())
        }.getOrNull()
        return buildList {
            codeSource?.parentFile?.let { libDirectory ->
                add(File(libDirectory, "resources/runtime/vlc"))
                add(File(libDirectory, "../resources/runtime/vlc"))
                add(File(libDirectory, "../runtime/vlc"))
            }
            add(File(System.getProperty("user.dir"), "app/resources/runtime/vlc"))
        }
    }

    private fun systemCandidates(): List<File> = when {
        System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> listOf(File("/Applications/VLC.app/Contents/MacOS"))
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> listOfNotNull(
            System.getenv("ProgramFiles")?.let { File(it, "VideoLAN/VLC") },
            System.getenv("ProgramFiles(x86)")?.let { File(it, "VideoLAN/VLC") }
        )
        else -> listOf(File("/usr/lib/x86_64-linux-gnu"), File("/usr/lib64"), File("/usr/lib"))
    }

    private fun isRuntime(home: File): Boolean = libraryCandidates(home).any(File::isFile)

    private fun configureNativePath(home: File) {
        val libraryDirectory = libraryCandidates(home).firstOrNull(File::isFile)?.parentFile ?: home
        val existing = System.getProperty("jna.library.path").orEmpty()
        System.setProperty("jna.library.path", listOf(libraryDirectory.absolutePath, existing).filter(String::isNotBlank).joinToString(File.pathSeparator))
    }

    private fun libraryCandidates(home: File): List<File> = when {
        System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> listOf(File(home, "lib/libvlc.dylib"), File(home, "libvlc.dylib"))
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> listOf(File(home, "libvlc.dll"))
        else -> listOf(File(home, "libvlc.so"), File(home, "lib/libvlc.so"))
    }

    private fun pluginDirectory(home: File): File? = listOf(File(home, "plugins"), File(home, "lib/vlc/plugins"), File(home, "lib/vlc/plugins"))
        .firstOrNull(File::isDirectory)
}

private fun BufferProfile.vlcOption(): String = when (this) {
    BufferProfile.LOW_LATENCY -> ":network-caching=300"
    BufferProfile.BALANCED -> ":network-caching=1000"
    BufferProfile.STABLE -> ":network-caching=3000"
}
