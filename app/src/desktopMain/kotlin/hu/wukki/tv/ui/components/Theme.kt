package hu.wukki.tv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme

val WukkiBlue = Color(0xFF4D7CFE)
val AppPanel = Color(0xFF172033)
val AppBackground = Color(0xFF0B1220)

val WukkiColorScheme = darkColorScheme(
    primary = WukkiBlue,
    onPrimary = Color.White,
    background = AppBackground,
    onBackground = Color.White,
    surface = AppPanel,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF17263A),
    onSurfaceVariant = Color.White,
    error = Color(0xFFFFB4AB),
    onError = Color.White
)
