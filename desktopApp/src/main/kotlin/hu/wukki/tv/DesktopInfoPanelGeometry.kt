package hu.wukki.tv

import kotlin.math.min

internal const val DESKTOP_INFO_PANEL_SCALE = 1.4f

internal data class DesktopInfoPanelGeometry(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val outerMargin: Int,
    val contentScale: Float
)

internal fun desktopInfoPanelGeometry(
    viewportWidth: Int,
    viewportHeight: Int,
    baseScale: Float
): DesktopInfoPanelGeometry {
    val safeWidth = viewportWidth.coerceAtLeast(1)
    val safeHeight = viewportHeight.coerceAtLeast(1)
    val safeBaseScale = baseScale.coerceAtLeast(.45f)
    val contentScale = safeBaseScale * DESKTOP_INFO_PANEL_SCALE
    val outerMargin = (PlaybackInfoPanelStyle.OUTER_MARGIN * safeBaseScale).toInt().coerceAtLeast(12)
    val availableWidth = (safeWidth - outerMargin * 2).coerceAtLeast(1)
    val availableHeight = (safeHeight - outerMargin * 2).coerceAtLeast(1)
    val maximumWidth = (PlaybackInfoPanelStyle.MAX_WIDTH * contentScale).toInt().coerceAtLeast(320)
    val width = min(
        (availableWidth * PlaybackInfoPanelStyle.WIDTH_FRACTION).toInt().coerceAtLeast(1),
        maximumWidth
    ).coerceAtMost(availableWidth)
    val height = (PlaybackInfoPanelStyle.MIN_HEIGHT * contentScale).toInt()
        .coerceAtLeast(90)
        .coerceAtMost(availableHeight)

    return DesktopInfoPanelGeometry(
        left = (safeWidth - width) / 2,
        top = safeHeight - outerMargin - height,
        width = width,
        height = height,
        outerMargin = outerMargin,
        contentScale = contentScale
    )
}
