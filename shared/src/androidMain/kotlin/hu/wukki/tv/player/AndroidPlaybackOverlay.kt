package hu.wukki.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.formatTime
import kotlin.math.max

@Composable
internal fun AndroidPlaybackOverlay(data: PlaybackOverlayData?, modifier: Modifier) {
    val overlay = data ?: return
    Box(modifier) {
        if (overlay.showBufferingSpinner) {
            Column(
                modifier = Modifier.align(Alignment.Center).background(WukkiColors.surfaceOverlay).padding(horizontal = 26.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = WukkiColors.primary)
                overlay.bufferingLabel?.let { label -> Spacer(Modifier.height(12.dp)); Text(label, color = WukkiColors.textPrimary) }
            }
        }
        if (overlay.channelNumberInput != null) {
            Text(
                overlay.channelNumberInput,
                color = WukkiColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).background(WukkiColors.surfaceOverlay).padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (overlay.showPreviewLogo && overlay.logoUrl != null) {
            AsyncImage(
                model = overlay.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).width(88.dp).height(42.dp)
            )
        }
        if (overlay.showProgrammeInfo) ProgrammePanel(overlay, Modifier.align(Alignment.BottomCenter).padding(28.dp))
        overlay.playbackStatus?.let { status ->
            Text(
                status,
                color = if (overlay.playbackError) WukkiColors.error else WukkiColors.textPrimary,
                modifier = Modifier.align(Alignment.Center).background(WukkiColors.surfaceOverlay).padding(16.dp)
            )
        }
    }
}

@Composable
private fun ProgrammePanel(data: PlaybackOverlayData, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(.92f).widthIn(max = 560.dp).background(WukkiColors.overlayPanel).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
            Text(data.channelNumber, color = WukkiColors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
            if (data.logoUrl != null) {
                AsyncImage(
                    model = data.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(64.dp).height(32.dp)
                )
            } else Text(data.channelName, color = WukkiColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(data.currentTitle ?: data.noEpgLabel, color = WukkiColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val progress = data.currentStart?.let { start -> data.currentEnd?.let { end -> ((data.now - start).toFloat() / max(1L, end - start)).coerceIn(0f, 1f) } }
            if (progress != null) {
                Text("${formatTime(data.currentStart)} – ${formatTime(data.currentEnd!!)}", color = WukkiColors.textSecondary, fontSize = 12.sp)
                LinearProgressIndicator(progress = { progress }, color = WukkiColors.primary, trackColor = WukkiColors.border, modifier = Modifier.fillMaxWidth().height(5.dp))
            }
            data.nextTitle?.let { next ->
                Text("${data.nextLabel}: $next", color = WukkiColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
