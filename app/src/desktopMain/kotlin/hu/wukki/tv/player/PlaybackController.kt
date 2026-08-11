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
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.imageio.ImageIO
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class PlaybackState {
    IDLE, OPENING, BUFFERING, PLAYING, RECONNECTING, ERROR
}

data class PlaybackOverlayData(
    val channelId: String,
    val channelNumber: String,
    val channelName: String,
    val logoUrl: String?,
    val showProgrammeInfo: Boolean,
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
    val playbackError: Boolean = false
)

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
    private val logoCache = ConcurrentHashMap<String, BufferedImage>()
    private val pendingLogos = ConcurrentHashMap.newKeySet<String>()
    private val failedLogos = ConcurrentHashMap.newKeySet<String>()
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
        val changedBuffer = currentSettings.bufferProfile != settings.bufferProfile
        currentChannel = channel
        currentSettings = settings
        currentShowLogos = showLogos
        applyAspectRatio(settings.aspectRatio ?: AspectRatioMode.AUTO)
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
        play(channel, settings, currentShowLogos)
    }

    /** Updates the Java2D video overlay without restarting or reconfiguring the stream. */
    fun updateOverlay(data: PlaybackOverlayData) {
        if (released) return
        val component = overlayComponent ?: return
        val logo = data.logoUrl?.let(logoCache::get)
        component.overlay = RenderedPlaybackOverlay(data, logo)
        requestRepaint()

        val logoUrl = data.logoUrl ?: return
        if (logo != null || logoUrl in failedLogos || !pendingLogos.add(logoUrl)) return
        logoExecutor.execute {
            val loaded = runCatching {
                URI.create(logoUrl).toURL().openConnection().apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                }.getInputStream().use(ImageIO::read)
            }.getOrNull()
            pendingLogos.remove(logoUrl)
            if (loaded != null) logoCache[logoUrl] = loaded else failedLogos.add(logoUrl)
            val current = component.overlay?.data
            if (!released && loaded != null && current?.channelId == data.channelId && current.logoUrl == logoUrl) {
                component.overlay = RenderedPlaybackOverlay(current, loaded)
                requestRepaint()
            }
        }
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

private data class RenderedPlaybackOverlay(val data: PlaybackOverlayData, val logo: BufferedImage? = null)

/** The overlay is painted by the same Swing component as the callback video, above every frame. */
private class OverlayCallbackMediaPlayerComponent(vararg factoryArguments: String) : CallbackMediaPlayerComponent(*factoryArguments) {
    @Volatile var overlay: RenderedPlaybackOverlay? = null

    override fun onPaintOverlay(graphics: Graphics2D) {
        super.onPaintOverlay(graphics)
        val content = overlay ?: return
        val data = content.data
        val surface = videoSurfaceComponent()
        val width = surface.width
        val height = surface.height
        if (width <= 0 || height <= 0) return

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        val scale = min(width / 1106f, height / 762f).coerceAtLeast(.45f)

        if (data.showProgrammeInfo) drawProgrammePanel(graphics, content, width, height, scale)
        data.playbackStatus?.let {
            drawPlaybackStatus(graphics, it, data.playbackError, data.showProgrammeInfo, width, height, scale)
        }
        data.channelNumberInput?.takeIf(String::isNotEmpty)?.let {
            drawChannelNumberInput(graphics, it, width, scale)
        }
    }
}

private fun drawChannelNumberInput(graphics: Graphics2D, number: String, width: Int, scale: Float) {
    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (48 * scale).toInt().coerceAtLeast(26))
    val metrics = graphics.fontMetrics
    val horizontalPadding = (24 * scale).toInt().coerceAtLeast(12)
    val verticalPadding = (14 * scale).toInt().coerceAtLeast(8)
    val boxWidth = (metrics.stringWidth(number) + horizontalPadding * 2).coerceAtLeast((86 * scale).toInt())
    val boxHeight = metrics.height + verticalPadding * 2
    val margin = (38 * scale).toInt().coerceAtLeast(18)
    val x = width - margin - boxWidth
    val y = margin
    val arc = (16 * scale).toInt().coerceAtLeast(10)

    graphics.color = Color(4, 12, 22, 225)
    graphics.fillRoundRect(x, y, boxWidth, boxHeight, arc, arc)
    graphics.stroke = BasicStroke((2 * scale).coerceAtLeast(1f))
    graphics.color = Color(139, 92, 246)
    graphics.drawRoundRect(x, y, boxWidth, boxHeight, arc, arc)
    graphics.color = Color.WHITE
    graphics.drawString(
        number,
        x + (boxWidth - metrics.stringWidth(number)) / 2,
        y + (boxHeight - metrics.height) / 2 + metrics.ascent
    )
}

private fun drawProgrammePanel(
    graphics: Graphics2D,
    content: RenderedPlaybackOverlay,
    width: Int,
    height: Int,
    scale: Float
) {
    val data = content.data
    val margin = (28 * scale).toInt().coerceAtLeast(12)
    val bottomMargin = (1 * scale).toInt().coerceAtLeast(1)
    val panelHeight = min((280 * scale).toInt(), (height * .38f).toInt()).coerceAtLeast((175 * scale).toInt())
    val panelWidth = width - margin * 2
    val top = height - bottomMargin - panelHeight
    val radius = (6 * scale).toInt().coerceAtLeast(4)
    val previousComposite = graphics.composite
    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .92f)
    graphics.color = Color(5, 14, 23)
    graphics.fillRoundRect(margin, top, panelWidth, panelHeight, radius, radius)
    graphics.composite = previousComposite
    graphics.color = Color(39, 55, 72)
    graphics.stroke = BasicStroke((1.2f * scale).coerceAtLeast(1f))
    graphics.drawRoundRect(margin, top, panelWidth, panelHeight, radius, radius)

    val leftWidth = min((184 * scale).toInt(), (panelWidth * .2f).toInt())
    val dividerX = margin + leftWidth
    graphics.drawLine(dividerX, top, dividerX, top + panelHeight)

    val numberFont = Font(Font.SANS_SERIF, Font.PLAIN, (58 * scale).toInt().coerceAtLeast(28))
    graphics.font = numberFont
    graphics.color = Color.WHITE
    drawCentered(graphics, data.channelNumber, margin, dividerX, top + (89 * scale).toInt())
    val logoLeft = margin + (16 * scale).toInt()
    val logoTop = top + (108 * scale).toInt()
    val logoMaxWidth = (leftWidth - (32 * scale).toInt()).coerceAtLeast(1)
    val logoMaxHeight = (58 * scale).toInt().coerceAtLeast(24)
    val logo = content.logo
    if (logo != null && logo.width > 0 && logo.height > 0) {
        val logoScale = min(logoMaxWidth / logo.width.toDouble(), logoMaxHeight / logo.height.toDouble())
        val drawnWidth = (logo.width * logoScale).toInt().coerceAtLeast(1)
        val drawnHeight = (logo.height * logoScale).toInt().coerceAtLeast(1)
        val logoX = logoLeft + (logoMaxWidth - drawnWidth) / 2
        val logoY = logoTop + (logoMaxHeight - drawnHeight) / 2
        graphics.drawImage(logo, logoX, logoY, drawnWidth, drawnHeight, null)
    } else {
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (21 * scale).toInt().coerceAtLeast(13))
        drawCentered(graphics, data.channelName, margin + 8, dividerX - 8, top + (145 * scale).toInt())
    }

    val contentLeft = dividerX + (38 * scale).toInt()
    val contentRight = margin + panelWidth - (30 * scale).toInt()
    val title = data.currentTitle ?: data.noEpgLabel
    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (30 * scale).toInt().coerceAtLeast(17))
    graphics.color = Color.WHITE
    drawClippedText(graphics, title, contentLeft, top + (55 * scale).toInt(), contentRight - contentLeft)

    val metaFont = Font(Font.SANS_SERIF, Font.PLAIN, (20 * scale).toInt().coerceAtLeast(12))
    graphics.font = metaFont
    graphics.color = Color(204, 210, 220)
    val timeY = top + (94 * scale).toInt()
    if (data.currentStart != null && data.currentEnd != null && data.currentEnd > data.currentStart) {
        val startText = overlayTime(data.currentStart)
        val endText = overlayTime(data.currentEnd)
        graphics.drawString(startText, contentLeft, timeY)
        val progressLeft = contentLeft + graphics.fontMetrics.stringWidth(startText) + (22 * scale).toInt()
        val progressRight = min(contentRight - graphics.fontMetrics.stringWidth(endText) - (185 * scale).toInt(), progressLeft + (430 * scale).toInt())
        if (progressRight > progressLeft) {
            val progressY = timeY - (8 * scale).toInt()
            val barHeight = (6 * scale).toInt().coerceAtLeast(3)
            val progress = ((data.now - data.currentStart).toDouble() / (data.currentEnd - data.currentStart)).coerceIn(0.0, 1.0)
            graphics.color = Color(44, 58, 75)
            graphics.fillRoundRect(progressLeft, progressY, progressRight - progressLeft, barHeight, barHeight, barHeight)
            graphics.color = Color(139, 92, 246)
            graphics.fillRoundRect(progressLeft, progressY, ((progressRight - progressLeft) * progress).toInt(), barHeight, barHeight, barHeight)
            graphics.color = Color(204, 210, 220)
            graphics.drawString(endText, progressRight + (16 * scale).toInt(), timeY)
        }
    } else {
        graphics.drawString(data.noEpgLabel, contentLeft, timeY)
    }

    val nowText = overlayTime(data.now)
    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (20 * scale).toInt().coerceAtLeast(12))
    graphics.color = Color.WHITE
    graphics.drawString(nowText, contentRight - graphics.fontMetrics.stringWidth(nowText), top + (50 * scale).toInt())
    data.remainingText?.let { remaining ->
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, (17 * scale).toInt().coerceAtLeast(11))
        graphics.color = Color(202, 208, 219)
        graphics.drawString(remaining, contentRight - graphics.fontMetrics.stringWidth(remaining), top + (84 * scale).toInt())
    }

    val horizontalDividerY = top + (132 * scale).toInt()
    graphics.color = Color(39, 55, 72)
    graphics.drawLine(contentLeft, horizontalDividerY, contentRight, horizontalDividerY)
    graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, (17 * scale).toInt().coerceAtLeast(11))
    graphics.color = Color(170, 179, 192)
    graphics.drawString("${data.nextLabel}:", contentLeft, horizontalDividerY + (43 * scale).toInt())
    data.nextTitle?.let { nextTitle ->
        val nextLeft = contentLeft + (170 * scale).toInt()
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (21 * scale).toInt().coerceAtLeast(13))
        graphics.color = Color.WHITE
        drawClippedText(graphics, nextTitle, nextLeft, horizontalDividerY + (43 * scale).toInt(), contentRight - nextLeft)
        if (data.nextStart != null && data.nextEnd != null) {
            graphics.font = metaFont
            graphics.color = Color(202, 208, 219)
            graphics.drawString(
                "${overlayTime(data.nextStart)}  –  ${overlayTime(data.nextEnd)}",
                nextLeft,
                horizontalDividerY + (78 * scale).toInt()
            )
        }
    }
}

private fun drawPlaybackStatus(
    graphics: Graphics2D,
    label: String,
    isError: Boolean,
    centered: Boolean,
    width: Int,
    height: Int,
    scale: Float
) {
    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (17 * scale).toInt().coerceAtLeast(12))
    val paddingX = (18 * scale).toInt()
    val boxHeight = (42 * scale).toInt().coerceAtLeast(28)
    val maxTextWidth = width - paddingX * 4
    val visibleLabel = clippedText(graphics, label, maxTextWidth)
    val boxWidth = graphics.fontMetrics.stringWidth(visibleLabel) + paddingX * 2
    val left = (width - boxWidth) / 2
    val top = if (centered) (height - boxHeight) / 2 else height - boxHeight - (20 * scale).toInt()
    graphics.color = if (isError) Color(86, 23, 30, 235) else Color(6, 16, 27, 225)
    graphics.fillRoundRect(left, top, boxWidth, boxHeight, 10, 10)
    graphics.color = if (isError) Color(255, 180, 171) else Color.WHITE
    graphics.drawString(visibleLabel, left + paddingX, top + (boxHeight + graphics.fontMetrics.ascent) / 2 - 3)
}

private fun drawCentered(graphics: Graphics2D, text: String, left: Int, right: Int, baseline: Int) {
    val clipped = clippedText(graphics, text, (right - left).coerceAtLeast(1))
    graphics.drawString(clipped, left + ((right - left) - graphics.fontMetrics.stringWidth(clipped)) / 2, baseline)
}

private fun drawClippedText(graphics: Graphics2D, text: String, x: Int, baseline: Int, maxWidth: Int) {
    graphics.drawString(clippedText(graphics, text, maxWidth), x, baseline)
}

private fun clippedText(graphics: Graphics2D, text: String, maxWidth: Int): String {
    if (graphics.fontMetrics.stringWidth(text) <= maxWidth) return text
    val ellipsis = "…"
    var end = text.length
    while (end > 0 && graphics.fontMetrics.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) end--
    return text.substring(0, end) + ellipsis
}

private val OverlayTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private fun overlayTime(timestamp: Long): String = OverlayTimeFormatter.format(Instant.ofEpochMilli(timestamp))

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
        SwingPanel(
            factory = {
                component.apply {
                    isFocusable = false
                    videoSurfaceComponent().isFocusable = false
                }
            },
            modifier = modifier
        )
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
