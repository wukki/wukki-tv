package hu.wukki.tv.ui.channels

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.ChannelListDisplayMode
import hu.wukki.tv.Programme

/** Immutable input for the Channels feature. Kept separate from the persistent domain model. */
data class ChannelBrowserUiState(
    val language: AppLanguage,
    val categories: List<String>,
    val query: String,
    val selectedCategory: String?,
    val onlyFavorites: Boolean,
    val channels: List<ChannelBrowserRowUiState>,
    val selectedChannelId: String?,
    val displayMode: ChannelListDisplayMode,
    val showChannelProgramme: Boolean,
    val showMiniGuide: Boolean,
    val showProgrammeImages: Boolean,
    val preview: ChannelPreviewUiState?
)

data class ChannelBrowserRowUiState(
    val channel: Channel,
    val position: Int,
    val currentProgramme: Programme?,
    val nextProgramme: Programme?
)

data class ChannelPreviewUiState(
    val channel: Channel,
    val currentProgramme: Programme?,
    val now: Long
)

data class ChannelBrowserCallbacks(
    val onQueryChange: (String) -> Unit,
    val onSelectAll: () -> Unit,
    val onSelectFavorites: () -> Unit,
    val onSelectCategory: (String) -> Unit,
    val onSelectChannel: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit
)
