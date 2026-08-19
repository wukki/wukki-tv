package hu.wukki.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.components.WukkiOverlayColors
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.awt.Color
import java.awt.AlphaComposite
import java.awt.geom.Arc2D
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

/**
 * Owns one libVLC instance for the full lifetime of the Compose application.
 * The Swing host may be removed while browsing other screens; audio and the stream keep running.
 */
class PlaybackController(initialLanguage: AppLanguage = AppLanguage.HUNGARIAN) {
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
    private var bufferingTask: ScheduledFuture<*>? = null
    private var spinnerRepaintTask: ScheduledFuture<*>? = null
    private var currentChannel: Channel? = null
    private var currentSettings: PlaybackSettings = PlaybackSettings()
    private var currentShowLogos = true
    private var currentLanguage = initialLanguage
    private var attempt = 0
    private var released = false

    var state by mutableStateOf(PlaybackState.IDLE)
        private set
    var detail by mutableStateOf<String?>(null)
        private set
    /** Set from libVLC's `playing` event; consumed by the Compose application layer. */
    var successfullyPlayedChannelId by mutableStateOf<String?>(null)
        private set

    private val runtimeResolution = VlcRuntimeResolver.resolve()
    private val runtime = runtimeResolution.runtime
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

    fun play(channel: Channel?, settings: PlaybackSettings, showLogos: Boolean = true, language: AppLanguage = AppLanguage.HUNGARIAN) {
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

    fun updateSettings(settings: PlaybackSettings) {
        val channel = currentChannel ?: return
        play(channel, settings, currentShowLogos, currentLanguage)
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
        cancelBufferingIndicator()
        component?.mediaPlayer()?.controls()?.stop()
        updateState(PlaybackState.IDLE, null)
    }

    fun release() {
        if (released) return
        released = true
        retryTask?.cancel(true)
        bufferingTask?.cancel(true)
        retryExecutor.shutdownNow()
        logoExecutor.shutdownNow()
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

        if (data.showPreviewLogo) drawPreviewLogo(graphics, content, width, scale)
        if (data.showProgrammeInfo) drawProgrammePanel(graphics, content, width, height, scale)
        if (data.showBufferingSpinner) drawBufferingSpinner(graphics, data.bufferingLabel.orEmpty(), width, height, scale)
        data.playbackStatus?.let {
            drawPlaybackStatus(graphics, it, data.playbackError, data.showProgrammeInfo, width, height, scale)
        }
        data.channelNumberInput?.takeIf(String::isNotEmpty)?.let {
            drawChannelNumberInput(graphics, it, width, scale)
        }
    }
}

private fun drawBufferingSpinner(graphics: Graphics2D, label: String, width: Int, height: Int, scale: Float) {
    val diameter = (52 * scale).toInt().coerceAtLeast(30)
    val x = (width - diameter) / 2
    val y = (height - diameter) / 2 - (14 * scale).toInt()
    val stroke = (5 * scale).coerceAtLeast(3f)
    graphics.stroke = BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    graphics.color = WukkiOverlayColors.divider
    graphics.draw(Arc2D.Float(x.toFloat(), y.toFloat(), diameter.toFloat(), diameter.toFloat(), 0f, 360f, Arc2D.OPEN))
    val rotation = ((System.currentTimeMillis() % 900L) * 360f / 900f)
    graphics.color = WukkiOverlayColors.accent
    graphics.draw(Arc2D.Float(x.toFloat(), y.toFloat(), diameter.toFloat(), diameter.toFloat(), -rotation, 105f, Arc2D.OPEN))
    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (17 * scale).toInt().coerceAtLeast(12))
    graphics.color = WukkiOverlayColors.text
    val baseline = y + diameter + graphics.fontMetrics.height + (9 * scale).toInt()
    graphics.drawString(label, (width - graphics.fontMetrics.stringWidth(label)) / 2, baseline)
}

private fun drawPreviewLogo(
    graphics: Graphics2D,
    content: RenderedPlaybackOverlay,
    width: Int,
    scale: Float
) {
    val margin = (24 * scale).toInt().coerceAtLeast(12)
    val maxWidth = min((190 * scale).toInt(), (width * .28f).toInt()).coerceAtLeast(48)
    val maxHeight = (58 * scale).toInt().coerceAtLeast(24)
    val logo = content.logo
    if (logo != null && logo.width > 0 && logo.height > 0) {
        val logoScale = min(maxWidth / logo.width.toDouble(), maxHeight / logo.height.toDouble())
        val drawnWidth = (logo.width * logoScale).toInt().coerceAtLeast(1)
        val drawnHeight = (logo.height * logoScale).toInt().coerceAtLeast(1)
        graphics.drawImage(logo, margin, margin, drawnWidth, drawnHeight, null)
    } else {
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (22 * scale).toInt().coerceAtLeast(13))
        graphics.color = Color.WHITE
        graphics.drawString(content.data.channelName, margin, margin + graphics.fontMetrics.ascent)
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

    graphics.color = WukkiOverlayColors.panel
    graphics.fillRoundRect(x, y, boxWidth, boxHeight, arc, arc)
    graphics.stroke = BasicStroke((2 * scale).coerceAtLeast(1f))
    graphics.color = WukkiOverlayColors.accent
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
    graphics.color = WukkiOverlayColors.panel
    graphics.fillRoundRect(margin, top, panelWidth, panelHeight, radius, radius)
    graphics.composite = previousComposite
    graphics.color = WukkiOverlayColors.divider
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
    graphics.color = WukkiOverlayColors.text
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
            graphics.color = WukkiOverlayColors.divider
            graphics.fillRoundRect(progressLeft, progressY, progressRight - progressLeft, barHeight, barHeight, barHeight)
            graphics.color = WukkiOverlayColors.accent
            graphics.fillRoundRect(progressLeft, progressY, ((progressRight - progressLeft) * progress).toInt(), barHeight, barHeight, barHeight)
            graphics.color = WukkiOverlayColors.text
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
        graphics.color = WukkiOverlayColors.text
        graphics.drawString(remaining, contentRight - graphics.fontMetrics.stringWidth(remaining), top + (84 * scale).toInt())
    }

    val horizontalDividerY = top + (132 * scale).toInt()
    graphics.color = WukkiOverlayColors.divider
    graphics.drawLine(contentLeft, horizontalDividerY, contentRight, horizontalDividerY)
    graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, (17 * scale).toInt().coerceAtLeast(11))
    graphics.color = WukkiOverlayColors.muted
    graphics.drawString("${data.nextLabel}:", contentLeft, horizontalDividerY + (43 * scale).toInt())
    data.nextTitle?.let { nextTitle ->
        val nextLeft = contentLeft + (170 * scale).toInt()
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (21 * scale).toInt().coerceAtLeast(13))
        graphics.color = Color.WHITE
        drawClippedText(graphics, nextTitle, nextLeft, horizontalDividerY + (43 * scale).toInt(), contentRight - nextLeft)
        if (data.nextStart != null && data.nextEnd != null) {
            graphics.font = metaFont
            graphics.color = WukkiOverlayColors.text
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
    graphics.color = if (isError) WukkiOverlayColors.errorPanel else WukkiOverlayColors.panel
    graphics.fillRoundRect(left, top, boxWidth, boxHeight, 10, 10)
    graphics.color = if (isError) WukkiOverlayColors.errorText else Color.WHITE
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

internal fun BufferProfile.vlcOption(): String = when (this) {
    BufferProfile.LOW_LATENCY -> ":network-caching=300"
    BufferProfile.BALANCED -> ":network-caching=1000"
    BufferProfile.STABLE -> ":network-caching=3000"
}
