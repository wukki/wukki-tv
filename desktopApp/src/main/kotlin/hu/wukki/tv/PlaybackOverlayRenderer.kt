package hu.wukki.tv

import hu.wukki.tv.ui.components.WukkiOverlayColors
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.math.min

internal data class RenderedPlaybackOverlay(
    val data: PlaybackOverlayData,
    val logo: BufferedImage? = null,
    val programmeImage: BufferedImage? = null
)

internal class DesktopPlaybackOverlayCoordinator(
    private val component: OverlayCallbackMediaPlayerComponent,
    private val requestRepaint: () -> Unit
) {
    private val imageExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wukki-vlc-overlay-image").apply { isDaemon = true }
    }
    private val imageCache = ConcurrentHashMap<String, BufferedImage>()
    private val pendingImages = ConcurrentHashMap.newKeySet<String>()
    private val failedImages = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var released = false

    fun update(data: PlaybackOverlayData) {
        if (released) return
        component.overlay = RenderedPlaybackOverlay(
            data = data,
            logo = data.logoUrl?.let(imageCache::get),
            programmeImage = data.programmeImageUrl?.let(imageCache::get)
        )
        requestRepaint()
        listOfNotNull(data.logoUrl, data.programmeImageUrl).distinct().forEach { loadImage(it, data.channelId) }
    }

    private fun loadImage(imageUrl: String, channelId: String) {
        if (imageCache.containsKey(imageUrl) || imageUrl in failedImages || !pendingImages.add(imageUrl)) return
        imageExecutor.execute {
            val loaded = runCatching {
                val connection = URI.create(imageUrl).toURL().openConnection().apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "WukkiTV/1.0 (desktop; image loader)")
                    setRequestProperty("Accept", "image/*")
                }
                (connection as? HttpURLConnection)?.let { http ->
                    require(http.responseCode in 200..299) { "HTTP ${http.responseCode}" }
                }
                require(connection.contentType?.startsWith("image/", ignoreCase = true) != false) {
                    "Unsupported content type: ${connection.contentType}"
                }
                connection.getInputStream().use(ImageIO::read)
            }.getOrNull()
            pendingImages.remove(imageUrl)
            if (loaded != null) imageCache[imageUrl] = loaded else failedImages.add(imageUrl)
            val current = component.overlay
            val imageStillUsed = current?.data?.let { it.logoUrl == imageUrl || it.programmeImageUrl == imageUrl } == true
            if (!released && loaded != null && current?.data?.channelId == channelId && imageStillUsed) {
                component.overlay = current.copy(
                    logo = current.data.logoUrl?.let(imageCache::get),
                    programmeImage = current.data.programmeImageUrl?.let(imageCache::get)
                )
                requestRepaint()
            }
        }
    }

    fun release() {
        released = true
        imageExecutor.shutdownNow()
    }
}

/** The overlay is painted by the same Swing component as the callback video, above every frame. */
internal class OverlayCallbackMediaPlayerComponent(vararg factoryArguments: String) : CallbackMediaPlayerComponent(*factoryArguments) {
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

    val channelColumnCenterX = margin + leftWidth / 2
    val arrowSize = (30 * scale).toInt().coerceAtLeast(18)
    drawChannelNavigationChevron(
        graphics = graphics,
        centerX = channelColumnCenterX,
        centerY = top + (24 * scale).toInt().coerceAtLeast(15),
        size = arrowSize,
        pointsUp = true,
        scale = scale
    )
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
    drawChannelNavigationChevron(
        graphics = graphics,
        centerX = channelColumnCenterX,
        centerY = top + panelHeight - (24 * scale).toInt().coerceAtLeast(15),
        size = arrowSize,
        pointsUp = false,
        scale = scale
    )

    val artwork = content.programmeImage
    val artworkGap = (24 * scale).toInt().coerceAtLeast(10)
    val artworkWidth = if (artwork != null) {
        min((220 * scale).toInt(), (panelWidth * .21f).toInt()).coerceAtLeast(80)
    } else 0
    if (artwork != null) {
        val artworkHeight = (artworkWidth * 9f / 16f).toInt().coerceAtLeast(45)
        drawCroppedImage(
            graphics = graphics,
            image = artwork,
            x = dividerX + artworkGap,
            y = top + (24 * scale).toInt().coerceAtLeast(10),
            width = artworkWidth,
            height = artworkHeight,
            radius = (8 * scale).toInt().coerceAtLeast(4)
        )
    }
    val contentLeft = if (artwork != null) {
        dividerX + artworkGap + artworkWidth + artworkGap
    } else {
        dividerX + (38 * scale).toInt()
    }
    val contentRight = margin + panelWidth - (30 * scale).toInt()
    val title = data.currentTitle ?: data.noEpgLabel
    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (30 * scale).toInt().coerceAtLeast(17))
    graphics.color = Color.WHITE
    drawClippedText(graphics, title, contentLeft, top + (55 * scale).toInt(), contentRight - contentLeft)

    val metaFont = Font(Font.SANS_SERIF, Font.PLAIN, (20 * scale).toInt().coerceAtLeast(12))
    graphics.font = metaFont
    graphics.color = WukkiOverlayColors.text
    val timeY = top + (94 * scale).toInt()
    val currentStart = data.currentStart
    val currentEnd = data.currentEnd
    if (currentStart != null && currentEnd != null && currentEnd > currentStart) {
        val startText = overlayTime(currentStart)
        val endText = overlayTime(currentEnd)
        graphics.drawString(startText, contentLeft, timeY)
        val progressLeft = contentLeft + graphics.fontMetrics.stringWidth(startText) + (22 * scale).toInt()
        val progressRight = min(contentRight - graphics.fontMetrics.stringWidth(endText) - (185 * scale).toInt(), progressLeft + (430 * scale).toInt())
        if (progressRight > progressLeft) {
            val progressY = timeY - (8 * scale).toInt()
            val barHeight = (6 * scale).toInt().coerceAtLeast(3)
            val progress = ((data.now - currentStart).toDouble() / (currentEnd - currentStart)).coerceIn(0.0, 1.0)
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
        val nextStart = data.nextStart
        val nextEnd = data.nextEnd
        if (nextStart != null && nextEnd != null) {
            graphics.font = metaFont
            graphics.color = WukkiOverlayColors.text
            graphics.drawString(
                "${overlayTime(nextStart)}  –  ${overlayTime(nextEnd)}",
                nextLeft,
                horizontalDividerY + (78 * scale).toInt()
            )
        }
    }
}

private fun drawChannelNavigationChevron(
    graphics: Graphics2D,
    centerX: Int,
    centerY: Int,
    size: Int,
    pointsUp: Boolean,
    scale: Float
) {
    val halfWidth = size / 2f
    val halfHeight = size / 4f
    val outerY = if (pointsUp) centerY + halfHeight else centerY - halfHeight
    val middleY = if (pointsUp) centerY - halfHeight else centerY + halfHeight
    val path = Path2D.Float().apply {
        moveTo(centerX - halfWidth, outerY)
        lineTo(centerX.toFloat(), middleY)
        lineTo(centerX + halfWidth, outerY)
    }
    graphics.color = WukkiOverlayColors.accent
    graphics.stroke = BasicStroke(
        (3.2f * scale).coerceAtLeast(2f),
        BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND
    )
    graphics.draw(path)
}

private fun drawCroppedImage(
    graphics: Graphics2D,
    image: BufferedImage,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    radius: Int
) {
    if (image.width <= 0 || image.height <= 0 || width <= 0 || height <= 0) return
    val targetRatio = width.toDouble() / height
    val sourceRatio = image.width.toDouble() / image.height
    val sourceWidth: Int
    val sourceHeight: Int
    val sourceX: Int
    val sourceY: Int
    if (sourceRatio > targetRatio) {
        sourceHeight = image.height
        sourceWidth = (sourceHeight * targetRatio).toInt()
        sourceX = (image.width - sourceWidth) / 2
        sourceY = 0
    } else {
        sourceWidth = image.width
        sourceHeight = (sourceWidth / targetRatio).toInt()
        sourceX = 0
        sourceY = (image.height - sourceHeight) / 2
    }
    val previousClip = graphics.clip
    graphics.clip(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), radius.toFloat(), radius.toFloat()))
    graphics.drawImage(
        image,
        x, y, x + width, y + height,
        sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight,
        null
    )
    graphics.clip = previousClip
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
