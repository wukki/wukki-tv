package hu.wukki.tv.ui.channels

import androidx.compose.runtime.Immutable
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.ChannelListDisplayMode
import hu.wukki.tv.Programme

/** Immutable input for the Channels feature. Kept separate from the persistent domain model. */
@Immutable
data class ChannelBrowserUiState(
    val language: AppLanguage,
    val categories: List<String>,
    val query: String,
    val selectedCategory: String?,
    val onlyFavorites: Boolean,
    val channels: List<ChannelBrowserRowUiState>,
    val displayMode: ChannelListDisplayMode,
    val showChannelProgramme: Boolean,
    val showMiniGuide: Boolean,
    val showLogos: Boolean,
    val showProgrammeImages: Boolean,
    val playingChannelId: String?,
    val emptyState: ChannelEmptyState?,
    val playlistRefreshing: Boolean,
    val preview: ChannelPreviewUiState?,
)

enum class ChannelEmptyState {
    NO_DATA,
    LOAD_FAILED,
    NO_SEARCH_RESULTS,
    NO_FAVORITES,
    NO_CATEGORY_RESULTS,
}

enum class ChannelEmptyAction {
    REFRESH,
    CLEAR_SEARCH,
    SHOW_ALL,
}

fun channelEmptyState(
    hasSourceChannels: Boolean,
    visibleChannelCount: Int,
    query: String,
    onlyFavorites: Boolean,
    selectedCategory: String?,
    playlistLoadFailed: Boolean,
): ChannelEmptyState? =
    when {
        visibleChannelCount > 0 -> null
        query.isNotBlank() -> ChannelEmptyState.NO_SEARCH_RESULTS
        onlyFavorites -> ChannelEmptyState.NO_FAVORITES
        selectedCategory != null -> ChannelEmptyState.NO_CATEGORY_RESULTS
        playlistLoadFailed -> ChannelEmptyState.LOAD_FAILED
        !hasSourceChannels -> ChannelEmptyState.NO_DATA
        else -> null
    }

fun ChannelEmptyState.action(): ChannelEmptyAction =
    when (this) {
        ChannelEmptyState.NO_DATA, ChannelEmptyState.LOAD_FAILED -> ChannelEmptyAction.REFRESH
        ChannelEmptyState.NO_SEARCH_RESULTS -> ChannelEmptyAction.CLEAR_SEARCH
        ChannelEmptyState.NO_FAVORITES, ChannelEmptyState.NO_CATEGORY_RESULTS -> ChannelEmptyAction.SHOW_ALL
    }

@Immutable
data class ChannelBrowserRowUiState(
    val channel: Channel,
    val position: Int,
    val currentProgramme: Programme?,
    val nextProgramme: Programme?,
)

@Immutable
data class ChannelPreviewUiState(
    val channel: Channel,
    val currentProgramme: Programme?,
    val now: Long,
)

@Immutable
data class ChannelBrowserCallbacks(
    val onQueryChange: (String) -> Unit,
    val onSelectAll: () -> Unit,
    val onSelectFavorites: () -> Unit,
    val onSelectCategory: (String) -> Unit,
    val onSelectChannel: (String) -> Unit,
    val onOpenChannel: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit,
    val onEmptyAction: (ChannelEmptyAction) -> Unit,
)
