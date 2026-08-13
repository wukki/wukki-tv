package hu.wukki.tv.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.awt.Color as AwtColor

/**
 * The application's one visual vocabulary. Feature UIs should use these semantic tokens instead
 * of declaring local hexadecimal colours, so focus, feedback and surfaces stay consistent.
 */
object WukkiColors {
    val background = Color(0xFF07101A)
    val backgroundRaised = Color(0xFF0B1622)
    val navigationBackground = Color(0xFF050C14)
    val surface = Color(0xFF101D2B)
    val surfaceRaised = Color(0xFF162638)
    val surfaceInput = Color(0xFF17283A)
    val surfaceSelected = Color(0xFF282040)
    val surfaceOverlay = Color(0xE906111B)
    val video = Color.Black
    val transparent = Color.Transparent

    val border = Color(0xFF26384B)
    val borderSubtle = Color(0xFF1D2C3B)
    val textPrimary = Color.White
    val textSecondary = Color(0xFFC5CDD8)
    val textMuted = Color(0xFF9BA9BE)
    val textDisabled = Color(0xFF68778B)

    val primary = Color(0xFF8B5CF6)
    val primaryStrong = Color(0xFFA277FF)
    val primaryMuted = Color(0xFF493574)
    val primaryContainer = Color(0xFF2D214D)
    val focus = primaryStrong

    val success = Color(0xFFB9F6CA)
    val successContainer = Color(0xE612352C)
    val error = Color(0xFFFFB4AB)
    val errorContainer = Color(0xE65F1D22)

    val overlayPanel = Color(0xE1040C16)
    val overlayDivider = Color(0xFF27374B)
    val overlayText = Color(0xFFCCD2DC)
    val overlayMuted = Color(0xFFAAB3C0)
}

object WukkiBrushes {
    fun appBackground() = Brush.linearGradient(
        listOf(WukkiColors.navigationBackground, WukkiColors.background, WukkiColors.navigationBackground)
    )
    fun navigationBackground() = Brush.horizontalGradient(
        listOf(WukkiColors.navigationBackground, WukkiColors.backgroundRaised, WukkiColors.background)
    )
    fun navigationSelected() = Brush.horizontalGradient(
        listOf(WukkiColors.primaryMuted.copy(alpha = .82f), WukkiColors.primaryContainer.copy(alpha = .58f), Color.Transparent)
    )
    fun selectedSurface() = Brush.horizontalGradient(listOf(WukkiColors.primaryMuted, WukkiColors.primaryContainer))
    fun accent() = Brush.verticalGradient(listOf(WukkiColors.primaryStrong, WukkiColors.primary))
}

val WukkiColorScheme: ColorScheme = darkColorScheme(
    primary = WukkiColors.primary,
    onPrimary = WukkiColors.textPrimary,
    primaryContainer = WukkiColors.primaryContainer,
    onPrimaryContainer = WukkiColors.textPrimary,
    background = WukkiColors.background,
    onBackground = WukkiColors.textPrimary,
    surface = WukkiColors.surface,
    onSurface = WukkiColors.textPrimary,
    surfaceVariant = WukkiColors.surfaceRaised,
    onSurfaceVariant = WukkiColors.textSecondary,
    outline = WukkiColors.border,
    error = WukkiColors.error,
    onError = WukkiColors.textPrimary,
    errorContainer = WukkiColors.errorContainer,
    onErrorContainer = WukkiColors.error
)

/** AWT equivalent of the Compose tokens for the VLC Java2D overlay. */
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
