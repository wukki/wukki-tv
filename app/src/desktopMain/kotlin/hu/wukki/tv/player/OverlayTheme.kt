package hu.wukki.tv.ui.components

import androidx.compose.ui.graphics.Color
import java.awt.Color as AwtColor

/** AWT equivalent of the Compose tokens for the desktop VLC Java2D overlay. */
object WukkiOverlayColors {
    val panel = WukkiColors.overlayPanel.toAwt()
    val accent = WukkiColors.primary.toAwt()
    val surface = WukkiColors.backgroundRaised.toAwt()
    val divider = WukkiColors.overlayDivider.toAwt()
    val text = WukkiColors.overlayText.toAwt()
    val muted = WukkiColors.overlayMuted.toAwt()
    val errorPanel = WukkiColors.errorContainer.toAwt()
    val errorText = WukkiColors.error.toAwt()
}

private fun Color.toAwt(): AwtColor = AwtColor(red, green, blue, alpha)
