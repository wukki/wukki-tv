package hu.wukki.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.rememberWukkiImageRequest
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
        overlay.channelNumberInput?.let { channelNumberInput ->
            Text(
                channelNumberInput,
                color = WukkiColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).background(WukkiColors.surfaceOverlay).padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (overlay.showPreviewLogo) {
            overlay.logoUrl?.let { logoUrl ->
                OverlayChannelLogo(
                    channelName = overlay.channelName,
                    logoUrl = logoUrl,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).width(88.dp).height(42.dp)
                )
            }
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
    var artworkFailed by remember(data.programmeImageUrl) { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth(PlaybackInfoPanelStyle.WIDTH_FRACTION)
            .widthIn(max = PlaybackInfoPanelStyle.MAX_WIDTH.dp)
            .heightIn(min = PlaybackInfoPanelStyle.MIN_HEIGHT.dp)
            .background(WukkiColors.overlayPanel)
            .padding(PlaybackInfoPanelStyle.CONTENT_PADDING.dp),
        horizontalArrangement = Arrangement.spacedBy(PlaybackInfoPanelStyle.COLUMN_GAP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PlaybackInfoPanelStyle.CHANNEL_ITEM_GAP.dp),
            modifier = Modifier.width(PlaybackInfoPanelStyle.CHANNEL_COLUMN_WIDTH.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = null,
                tint = WukkiColors.primary,
                modifier = Modifier.size(PlaybackInfoPanelStyle.CHANNEL_ARROW_SIZE.dp)
            )
            Text(
                data.channelNumber,
                color = WukkiColors.textPrimary,
                fontSize = PlaybackInfoPanelStyle.CHANNEL_NUMBER_TEXT_SIZE.sp,
                fontWeight = FontWeight.Black
            )
            data.logoUrl?.let { logoUrl ->
                OverlayChannelLogo(
                    channelName = data.channelName,
                    logoUrl = logoUrl,
                    modifier = Modifier
                        .width(PlaybackInfoPanelStyle.CHANNEL_LOGO_WIDTH.dp)
                        .height(PlaybackInfoPanelStyle.CHANNEL_LOGO_HEIGHT.dp)
                )
            } ?: Text(data.channelName, color = WukkiColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = WukkiColors.primary,
                modifier = Modifier.size(PlaybackInfoPanelStyle.CHANNEL_ARROW_SIZE.dp)
            )
        }
        data.programmeImageUrl?.takeUnless { artworkFailed }?.let { imageUrl ->
            AsyncImage(
                model = rememberWukkiImageRequest(imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { artworkFailed = true },
                modifier = Modifier
                    .width(PlaybackInfoPanelStyle.ARTWORK_WIDTH.dp)
                    .aspectRatio(PlaybackInfoPanelStyle.ARTWORK_ASPECT_RATIO)
                    .clip(RoundedCornerShape(PlaybackInfoPanelStyle.ARTWORK_CORNER_RADIUS.dp))
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PlaybackInfoPanelStyle.ITEM_GAP.dp)
        ) {
            Text(
                data.currentTitle ?: data.noEpgLabel,
                color = WukkiColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = PlaybackInfoPanelStyle.TITLE_TEXT_SIZE.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val currentStart = data.currentStart
            val currentEnd = data.currentEnd
            if (currentStart != null && currentEnd != null) {
                val progress = ((data.now - currentStart).toFloat() / max(1L, currentEnd - currentStart)).coerceIn(0f, 1f)
                Text(
                    "${formatTime(currentStart)} – ${formatTime(currentEnd)}",
                    color = WukkiColors.textSecondary,
                    fontSize = PlaybackInfoPanelStyle.META_TEXT_SIZE.sp
                )
                LinearProgressIndicator(
                    progress = { progress },
                    color = WukkiColors.primary,
                    trackColor = WukkiColors.border,
                    modifier = Modifier.fillMaxWidth().height(PlaybackInfoPanelStyle.PROGRESS_HEIGHT.dp)
                )
            }
            data.nextTitle?.let { next ->
                Text(
                    "${data.nextLabel}: $next",
                    color = WukkiColors.textSecondary,
                    fontSize = PlaybackInfoPanelStyle.NEXT_TEXT_SIZE.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun OverlayChannelLogo(channelName: String, logoUrl: String, modifier: Modifier) {
    SubcomposeAsyncImage(
        model = rememberWukkiImageRequest(logoUrl),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        loading = { OverlayLogoFallback(channelName) },
        error = { OverlayLogoFallback(channelName) }
    )
}

@Composable
private fun OverlayLogoFallback(channelName: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = channelName,
            color = WukkiColors.textSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
