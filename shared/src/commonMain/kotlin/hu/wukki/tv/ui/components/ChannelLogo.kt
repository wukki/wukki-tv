package hu.wukki.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.PlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel

@Composable
fun ChannelLogo(channel: Channel, language: AppLanguage, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (channel.logo.isNullOrBlank()) LogoFallback(channel, language) else {
            SubcomposeAsyncImage(
                model = rememberWukkiImageRequest(channel.logo),
                contentDescription = tr(language, "logo.description", channel.displayName(language)),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = { LogoFallback(channel, language) },
                error = { LogoFallback(channel, language) }
            )
        }
    }
}

private val WukkiImageHeaders = NetworkHeaders.Builder().apply {
    this["User-Agent"] = "WukkiTV/1.0"
    this["Accept"] = "image/*"
}.build()

@Composable
fun rememberWukkiImageRequest(url: String): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, url) {
        wukkiImageRequest(context, url)
    }
}

fun wukkiImageRequest(
    context: PlatformContext,
    data: Any,
    width: Int? = null,
    height: Int? = null
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(data)
        .httpHeaders(WukkiImageHeaders)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
    if (width != null && height != null) builder.size(width, height)
    return builder.build()
}

@Composable
private fun LogoFallback(channel: Channel, language: AppLanguage) {
    val name = channel.displayName(language)
    androidx.compose.material3.Text(name.trim().firstOrNull()?.uppercase() ?: "TV", color = WukkiColors.textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
}

fun formatTime(millis: Long): String = Localizer.formatTime(millis)
