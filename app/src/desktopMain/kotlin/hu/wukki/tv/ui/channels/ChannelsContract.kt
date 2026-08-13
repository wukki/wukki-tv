package hu.wukki.tv.ui.channels

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.Programme

/** Immutable input for the channel-browser feature. */
data class ChannelBrowserUiState(
    val language: AppLanguage,
    val channels: List<ChannelRowUiState>,
    val categories: List<String>,
    val query: String,
    val selectedCategory: String?,
    val onlyFavorites: Boolean,
    val selectedChannel: ChannelProgrammeUiState?,
    val showChannelProgramme: Boolean,
    val showMiniGuide: Boolean,
    val now: Long
)

data class ChannelRowUiState(
    val channel: Channel,
    val position: Int,
    val currentProgramme: Programme?
)

data class ChannelProgrammeUiState(
    val channel: Channel,
    val currentProgramme: Programme?
)

interface ChannelBrowserActions {
    fun selectChannel(channelId: String)
    fun toggleFavorite(channelId: String)
    fun setQuery(query: String)
    fun showAll()
    fun showFavorites()
    fun selectCategory(category: String)
    fun clearSearch()
}
