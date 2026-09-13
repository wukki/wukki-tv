package hu.wukki.tv.ui.channels

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.wukki.tv.ui.components.WukkiColors
import hu.wukki.tv.ui.components.displayCategoryName
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus

@Composable
internal fun ChannelEmptyContent(
    state: ChannelBrowserUiState,
    callbacks: ChannelBrowserCallbacks,
    remoteFocus: ChannelRemoteFocus,
    scale: Float,
) {
    val emptyState = state.emptyState ?: ChannelEmptyState.NO_DATA
    val action = emptyState.action()
    val titleKey = emptyState.titleKey()
    val description = emptyState.description(state)
    val actionLabel = emptyState.actionLabel(state)
    val refreshing = action == ChannelEmptyAction.REFRESH && state.playlistRefreshing
    val buttonShape = RoundedCornerShape(10.dp * scale)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp).padding(24.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp * scale),
        ) {
            Text(
                tr(state.language, titleKey),
                fontSize = (21f * scale).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                description,
                color = WukkiColors.textMuted,
                fontSize = (14f * scale).sp,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { callbacks.onEmptyAction(action) },
                enabled = !refreshing,
                shape = buttonShape,
                modifier = Modifier.then(
                    if (remoteFocus == ChannelRemoteFocus.LIST) {
                        Modifier.border(3.dp, WukkiColors.focus, buttonShape)
                    } else {
                        Modifier
                    },
                ),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp * scale),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp * scale))
                }
                Text(actionLabel)
            }
        }
    }
}

private fun ChannelEmptyState.titleKey(): String =
    when (this) {
        ChannelEmptyState.NO_DATA -> "channels.empty.no.data.title"
        ChannelEmptyState.LOAD_FAILED -> "channels.empty.load.failed.title"
        ChannelEmptyState.NO_SEARCH_RESULTS -> "channels.empty.search.title"
        ChannelEmptyState.NO_FAVORITES -> "channels.empty.favorites.title"
        ChannelEmptyState.NO_CATEGORY_RESULTS -> "channels.empty.category.title"
    }

private fun ChannelEmptyState.description(state: ChannelBrowserUiState): String =
    when (this) {
        ChannelEmptyState.NO_DATA -> tr(state.language, "channels.empty.no.data.description")
        ChannelEmptyState.LOAD_FAILED -> tr(state.language, "channels.empty.load.failed.description")
        ChannelEmptyState.NO_SEARCH_RESULTS -> tr(state.language, "channels.empty.search.description", state.query)
        ChannelEmptyState.NO_FAVORITES -> tr(state.language, "channels.empty.favorites.description")
        ChannelEmptyState.NO_CATEGORY_RESULTS ->
            tr(
                state.language,
                "channels.empty.category.description",
                state.selectedCategory?.displayCategoryName(state.language).orEmpty(),
            )
    }

private fun ChannelEmptyState.actionLabel(state: ChannelBrowserUiState): String =
    when (action()) {
        ChannelEmptyAction.REFRESH -> tr(state.language, if (this == ChannelEmptyState.LOAD_FAILED) "action.retry" else "settings.refresh")
        ChannelEmptyAction.CLEAR_SEARCH -> tr(state.language, "channels.search.clear")
        ChannelEmptyAction.SHOW_ALL -> tr(state.language, "channels.show.all")
    }
