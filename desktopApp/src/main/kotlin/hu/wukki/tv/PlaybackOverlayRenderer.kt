package hu.wukki.tv

import hu.wukki.tv.ui.components.WukkiOverlayColors
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min

internal data class RenderedPlaybackOverlay(
    val data: PlaybackOverlayData,
    val logo: BufferedImage? = null,
    val programmeImage: BufferedImage? = null
)

internal class DesktopPlaybackOverlayCoordinator(
    private val component: OverlayCallbackMediaPlayerComponent,
    private val requestRepaint: () -> Unit,
    private val imageLoader: DesktopOverlayImageLoader = DesktopOverlayImageLoader()
) {
    private val imageExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wukki-vlc-overlay-image").apply { isDaemon = true }
    }
    private val imageCache = ConcurrentHashMap<String, BufferedImage>()
    private val pendingImages = ConcurrentHashMap.newKeySet<String>()
    private val failedImages = ConcurrentHashMap<String, Long>()
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
        if (imageCache.containsKey(imageUrl) || !canRetry(imageUrl) || !pendingImages.add(imageUrl)) return
        imageExecutor.execute {
            val loaded = runCatching { imageLoader.load(imageUrl) }.getOrNull()
            pendingImages.remove(imageUrl)
            if (loaded != null) {
                imageCache[imageUrl] = loaded
                failedImages.remove(imageUrl)
            } else {
                failedImages[imageUrl] = System.currentTimeMillis()
            }
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

    private fun canRetry(imageUrl: String): Boolean {
        val failedAt = failedImages[imageUrl] ?: return true
        return System.currentTimeMillis() - failedAt >= IMAGE_RETRY_DELAY_MILLIS
    }

    fun release() {
        released = true
        imageExecutor.shutdownNow()
        imageLoader.close()
    }

    private companion object {
        const val IMAGE_RETRY_DELAY_MILLIS = 30_000L
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
    fun scaled(value: Float, minimum: Int = 1): Int = (value * scale).toInt().coerceAtLeast(minimum)

    val outerMargin = scaled(PlaybackInfoPanelStyle.OUTER_MARGIN, 12)
    val maximumPanelWidth = scaled(PlaybackInfoPanelStyle.MAX_WIDTH, 320)
    val panelWidth = min((width * PlaybackInfoPanelStyle.WIDTH_FRACTION).toInt(), maximumPanelWidth)
        .coerceAtMost((width - outerMargin * 2).coerceAtLeast(1))
    val panelHeight = scaled(PlaybackInfoPanelStyle.MIN_HEIGHT, 90)
        .coerceAtMost((height - outerMargin * 2).coerceAtLeast(1))
    val margin = (width - panelWidth) / 2
    val bottomMargin = outerMargin
    val top = height - bottomMargin - panelHeight
    graphics.color = WukkiOverlayColors.panel
    graphics.fillRect(margin, top, panelWidth, panelHeight)

    val padding = scaled(PlaybackInfoPanelStyle.CONTENT_PADDING, 8)
    val columnGap = scaled(PlaybackInfoPanelStyle.COLUMN_GAP, 8)
    val channelGap = scaled(PlaybackInfoPanelStyle.CHANNEL_ITEM_GAP)
    val channelWidth = scaled(PlaybackInfoPanelStyle.CHANNEL_COLUMN_WIDTH, 38)
    val channelLeft = margin + padding
    val channelRight = channelLeft + channelWidth
    val channelColumnCenterX = (channelLeft + channelRight) / 2
    val arrowSize = scaled(PlaybackInfoPanelStyle.CHANNEL_ARROW_SIZE, 14)
    val numberFont = Font(Font.SANS_SERIF, Font.BOLD, scaled(PlaybackInfoPanelStyle.CHANNEL_NUMBER_TEXT_SIZE, 16))
    val logoWidth = scaled(PlaybackInfoPanelStyle.CHANNEL_LOGO_WIDTH, 34)
    val logoHeight = scaled(PlaybackInfoPanelStyle.CHANNEL_LOGO_HEIGHT, 17)
    val numberHeight = graphics.getFontMetrics(numberFont).height
    val channelContentHeight = arrowSize * 2 + numberHeight + logoHeight + channelGap * 3
    var channelY = top + (panelHeight - channelContentHeight) / 2

    drawChannelNavigationChevron(
        graphics = graphics,
        centerX = channelColumnCenterX,
        centerY = channelY + arrowSize / 2,
        size = arrowSize,
        pointsUp = true,
        scale = scale
    )
    channelY += arrowSize + channelGap
    graphics.font = numberFont
    graphics.color = Color.WHITE
    drawCentered(graphics, data.channelNumber, channelLeft, channelRight, channelY + graphics.fontMetrics.ascent)
    channelY += numberHeight + channelGap

    val logo = content.logo
    if (logo != null && logo.width > 0 && logo.height > 0) {
        val logoScale = min(logoWidth / logo.width.toDouble(), logoHeight / logo.height.toDouble())
        val drawnWidth = (logo.width * logoScale).toInt().coerceAtLeast(1)
        val drawnHeight = (logo.height * logoScale).toInt().coerceAtLeast(1)
        val logoX = channelLeft + (channelWidth - drawnWidth) / 2
        val logoY = channelY + (logoHeight - drawnHeight) / 2
        graphics.drawImage(logo, logoX, logoY, drawnWidth, drawnHeight, null)
    } else {
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, scaled(PlaybackInfoPanelStyle.META_TEXT_SIZE, 9))
        drawCentered(
            graphics,
            data.channelName,
            channelLeft,
            channelRight,
            channelY + (logoHeight - graphics.fontMetrics.height) / 2 + graphics.fontMetrics.ascent
        )
    }
    channelY += logoHeight + channelGap
    drawChannelNavigationChevron(
        graphics = graphics,
        centerX = channelColumnCenterX,
        centerY = channelY + arrowSize / 2,
        size = arrowSize,
        pointsUp = false,
        scale = scale
    )

    val artwork = content.programmeImage
    val artworkWidth = scaled(PlaybackInfoPanelStyle.ARTWORK_WIDTH, 76)
    val artworkHeight = (artworkWidth / PlaybackInfoPanelStyle.ARTWORK_ASPECT_RATIO).toInt().coerceAtLeast(43)
    val artworkLeft = channelRight + columnGap
    if (artwork != null) {
        drawCroppedImage(
            graphics = graphics,
            image = artwork,
            x = artworkLeft,
            y = top + (panelHeight - artworkHeight) / 2,
            width = artworkWidth,
            height = artworkHeight,
            radius = scaled(PlaybackInfoPanelStyle.ARTWORK_CORNER_RADIUS, 4)
        )
    }
    val contentLeft = if (artwork != null) {
        artworkLeft + artworkWidth + columnGap
    } else {
        channelRight + columnGap
    }
    val contentRight = margin + panelWidth - padding
    val availableContentWidth = (contentRight - contentLeft).coerceAtLeast(1)
    val title = data.currentTitle ?: data.noEpgLabel
    val titleFont = Font(Font.SANS_SERIF, Font.BOLD, scaled(PlaybackInfoPanelStyle.TITLE_TEXT_SIZE, 12))
    val metaFont = Font(Font.SANS_SERIF, Font.PLAIN, scaled(PlaybackInfoPanelStyle.META_TEXT_SIZE, 9))
    val nextFont = Font(Font.SANS_SERIF, Font.PLAIN, scaled(PlaybackInfoPanelStyle.NEXT_TEXT_SIZE, 9))
    val itemGap = scaled(PlaybackInfoPanelStyle.ITEM_GAP, 2)
    val progressHeight = scaled(PlaybackInfoPanelStyle.PROGRESS_HEIGHT, 3)
    val currentStart = data.currentStart
    val currentEnd = data.currentEnd
    val hasTiming = currentStart != null && currentEnd != null && currentEnd > currentStart
    val hasNext = data.nextTitle != null
    val contentHeight = graphics.getFontMetrics(titleFont).height +
        (if (hasTiming) itemGap + graphics.getFontMetrics(metaFont).height + itemGap + progressHeight else 0) +
        (if (hasNext) itemGap + graphics.getFontMetrics(nextFont).height else 0)
    var contentY = top + (panelHeight - contentHeight) / 2

    graphics.font = titleFont
    graphics.color = Color.WHITE
    drawClippedText(graphics, title, contentLeft, contentY + graphics.fontMetrics.ascent, availableContentWidth)
    contentY += graphics.fontMetrics.height

    if (hasTiming) {
        contentY += itemGap
        graphics.font = metaFont
        graphics.color = WukkiOverlayColors.text
        graphics.drawString("${overlayTime(currentStart)} – ${overlayTime(currentEnd)}", contentLeft, contentY + graphics.fontMetrics.ascent)
        contentY += graphics.fontMetrics.height + itemGap
        val progress = ((data.now - currentStart).toDouble() / (currentEnd - currentStart)).coerceIn(0.0, 1.0)
        graphics.color = WukkiOverlayColors.divider
        graphics.fillRoundRect(contentLeft, contentY, availableContentWidth, progressHeight, progressHeight, progressHeight)
        graphics.color = WukkiOverlayColors.accent
        graphics.fillRoundRect(
            contentLeft,
            contentY,
            (availableContentWidth * progress).toInt(),
            progressHeight,
            progressHeight,
            progressHeight
        )
        contentY += progressHeight
    }
    data.nextTitle?.let { nextTitle ->
        contentY += itemGap
        graphics.font = nextFont
        graphics.color = WukkiOverlayColors.text
        drawClippedText(
            graphics,
            "${data.nextLabel}: $nextTitle",
            contentLeft,
            contentY + graphics.fontMetrics.ascent,
            availableContentWidth
        )
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
