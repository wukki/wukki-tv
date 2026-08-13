package hu.wukki.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import hu.wukki.tv.Channel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.logging.Logger

private val logoLogger = Logger.getLogger("hu.wukki.tv.ChannelLogo")

@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (channel.logo.isNullOrBlank()) LogoFallback(channel) else {
            SubcomposeAsyncImage(
                model = channel.logo,
                contentDescription = "${channel.name} logója",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = { LogoFallback(channel) },
                error = { LogoFallback(channel) },
                onError = { state -> logoLogger.log(Level.WARNING, "Nem tölthető be a csatornalogó: channel=${channel.name}, url=${channel.logo}", state.result.throwable) }
            )
        }
    }
}

@Composable
private fun LogoFallback(channel: Channel) {
    androidx.compose.material3.Text(channel.name.trim().firstOrNull()?.uppercase() ?: "TV", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
}

fun formatTime(millis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
