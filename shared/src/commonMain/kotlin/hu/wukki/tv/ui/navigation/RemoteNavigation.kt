package hu.wukki.tv.ui.navigation

import hu.wukki.tv.ui.settings.SettingsSection

enum class RemoteKey { UP, DOWN, LEFT, RIGHT, CONFIRM }

data class MainMenuNavigationState(val index: Int)

sealed interface MainMenuNavigationEffect {
    data object None : MainMenuNavigationEffect
    data object EnterContent : MainMenuNavigationEffect
    data class Activate(val index: Int) : MainMenuNavigationEffect
}

data class MainMenuNavigationResult(
    val state: MainMenuNavigationState,
    val effect: MainMenuNavigationEffect = MainMenuNavigationEffect.None,
    val handled: Boolean = true
)

fun MainMenuNavigationState.reduce(key: RemoteKey, itemCount: Int): MainMenuNavigationResult {
    if (itemCount <= 0) return MainMenuNavigationResult(this, handled = false)
    val safeIndex = index.coerceIn(0, itemCount - 1)
    return when (key) {
        RemoteKey.UP -> MainMenuNavigationResult(copy(index = (safeIndex - 1).coerceAtLeast(0)))
        RemoteKey.DOWN -> MainMenuNavigationResult(copy(index = (safeIndex + 1).coerceAtMost(itemCount - 1)))
        RemoteKey.RIGHT -> MainMenuNavigationResult(copy(index = safeIndex), MainMenuNavigationEffect.EnterContent)
        RemoteKey.CONFIRM -> MainMenuNavigationResult(copy(index = safeIndex), MainMenuNavigationEffect.Activate(safeIndex))
        RemoteKey.LEFT -> MainMenuNavigationResult(copy(index = safeIndex), handled = false)
    }
}

data class ChannelNavigationState(
    val focus: ChannelRemoteFocus,
    val filterIndex: Int,
    val channelIndex: Int
)

sealed interface ChannelNavigationEffect {
    data object None : ChannelNavigationEffect
    data object ExitToMainMenu : ChannelNavigationEffect
    data class ActivateFilter(val index: Int) : ChannelNavigationEffect
    data class OpenChannel(val index: Int) : ChannelNavigationEffect
    data class ToggleFavorite(val index: Int) : ChannelNavigationEffect
}

data class ChannelNavigationResult(
    val state: ChannelNavigationState,
    val effect: ChannelNavigationEffect = ChannelNavigationEffect.None,
    val handled: Boolean = true
)

fun ChannelNavigationState.reduce(
    key: RemoteKey,
    filterCount: Int,
    channelCount: Int,
    searchHasText: Boolean
): ChannelNavigationResult {
    val lastFilter = (filterCount - 1).coerceAtLeast(0)
    val lastChannel = (channelCount - 1).coerceAtLeast(0)
    val safe = copy(filterIndex = filterIndex.coerceIn(0, lastFilter), channelIndex = channelIndex.coerceIn(0, lastChannel))
    return when (safe.focus) {
        ChannelRemoteFocus.FILTERS -> when (key) {
            RemoteKey.LEFT -> if (safe.filterIndex == 0) {
                ChannelNavigationResult(safe, ChannelNavigationEffect.ExitToMainMenu)
            } else ChannelNavigationResult(safe.copy(filterIndex = safe.filterIndex - 1))
            RemoteKey.RIGHT -> if (safe.filterIndex >= lastFilter) {
                ChannelNavigationResult(safe.copy(focus = ChannelRemoteFocus.SEARCH))
            } else ChannelNavigationResult(safe.copy(filterIndex = safe.filterIndex + 1))
            RemoteKey.DOWN -> ChannelNavigationResult(safe.copy(focus = ChannelRemoteFocus.LIST))
            RemoteKey.CONFIRM -> ChannelNavigationResult(safe, ChannelNavigationEffect.ActivateFilter(safe.filterIndex))
            RemoteKey.UP -> ChannelNavigationResult(safe, handled = false)
        }
        ChannelRemoteFocus.SEARCH -> when (key) {
            RemoteKey.LEFT, RemoteKey.RIGHT, RemoteKey.DOWN -> if (searchHasText) {
                ChannelNavigationResult(safe, handled = false)
            } else {
                val target = if (key == RemoteKey.LEFT) ChannelRemoteFocus.FILTERS else ChannelRemoteFocus.LIST
                ChannelNavigationResult(safe.copy(focus = target))
            }
            else -> ChannelNavigationResult(safe, handled = false)
        }
        ChannelRemoteFocus.LIST -> when (key) {
            RemoteKey.LEFT -> ChannelNavigationResult(safe, ChannelNavigationEffect.ExitToMainMenu)
            RemoteKey.RIGHT -> ChannelNavigationResult(safe.copy(focus = ChannelRemoteFocus.FAVORITE))
            RemoteKey.UP -> if (safe.channelIndex == 0) {
                ChannelNavigationResult(safe.copy(focus = ChannelRemoteFocus.FILTERS))
            } else ChannelNavigationResult(safe.copy(channelIndex = safe.channelIndex - 1))
            RemoteKey.DOWN -> ChannelNavigationResult(safe.copy(channelIndex = (safe.channelIndex + 1).coerceAtMost(lastChannel)))
            RemoteKey.CONFIRM -> ChannelNavigationResult(safe, ChannelNavigationEffect.OpenChannel(safe.channelIndex))
        }
        ChannelRemoteFocus.FAVORITE -> when (key) {
            RemoteKey.LEFT -> ChannelNavigationResult(safe.copy(focus = ChannelRemoteFocus.LIST))
            RemoteKey.UP -> if (safe.channelIndex == 0) {
                ChannelNavigationResult(safe.copy(focus = ChannelRemoteFocus.FILTERS))
            } else ChannelNavigationResult(safe.copy(channelIndex = safe.channelIndex - 1))
            RemoteKey.DOWN -> ChannelNavigationResult(safe.copy(channelIndex = (safe.channelIndex + 1).coerceAtMost(lastChannel)))
            RemoteKey.CONFIRM -> ChannelNavigationResult(safe, ChannelNavigationEffect.ToggleFavorite(safe.channelIndex))
            RemoteKey.RIGHT -> ChannelNavigationResult(safe, handled = false)
        }
    }
}

sealed interface SettingsOptionId {
    val section: SettingsSection
}

enum class PlaybackSettingsOption : SettingsOptionId {
    AUTOPLAY, VOLUME, BUFFER, ASPECT_RATIO, RECONNECT, RETRIES;
    override val section = SettingsSection.PLAYBACK
}

enum class DisplaySettingsOption : SettingsOptionId {
    UI_SCALE, CHANNEL_LIST, PROGRAMME, MINI_GUIDE, LOGOS, PROGRAMME_IMAGES;
    override val section = SettingsSection.DISPLAY
}

enum class EpgSettingsOption : SettingsOptionId {
    SCHEDULE, REFRESH;
    override val section = SettingsSection.EPG
}

enum class PlaylistSettingsOption : SettingsOptionId {
    SCHEDULE, REFRESH;
    override val section = SettingsSection.PLAYLISTS
}

enum class LanguageSettingsOption : SettingsOptionId {
    LANGUAGE;
    override val section = SettingsSection.LANGUAGE
}

fun SettingsSection.options(): List<SettingsOptionId> = when (this) {
    SettingsSection.PLAYBACK -> PlaybackSettingsOption.entries
    SettingsSection.EPG -> EpgSettingsOption.entries
    SettingsSection.DISPLAY -> DisplaySettingsOption.entries
    SettingsSection.PLAYLISTS -> PlaylistSettingsOption.entries
    SettingsSection.LANGUAGE -> LanguageSettingsOption.entries
    SettingsSection.PARENTAL, SettingsSection.ABOUT -> emptyList()
}

data class SettingsNavigationState(
    val section: SettingsSection? = null,
    val categoryIndex: Int = 0,
    val option: SettingsOptionId? = null
) {
    val optionIndex: Int
        get() = section?.options()?.indexOf(option)?.coerceAtLeast(0) ?: 0
}

sealed interface SettingsNavigationEffect {
    data object None : SettingsNavigationEffect
    data object ExitToMainMenu : SettingsNavigationEffect
    data class Activate(val option: SettingsOptionId) : SettingsNavigationEffect
    data class Adjust(val option: SettingsOptionId, val delta: Int) : SettingsNavigationEffect
}

data class SettingsNavigationResult(
    val state: SettingsNavigationState,
    val effect: SettingsNavigationEffect = SettingsNavigationEffect.None,
    val handled: Boolean = true
)

fun SettingsNavigationState.reduce(key: RemoteKey): SettingsNavigationResult {
    val lastCategory = SettingsSection.entries.lastIndex
    if (section == null) {
        val safeCategory = categoryIndex.coerceIn(0, lastCategory)
        return when (key) {
            RemoteKey.UP -> SettingsNavigationResult(copy(categoryIndex = (safeCategory - 1).coerceAtLeast(0)))
            RemoteKey.DOWN -> SettingsNavigationResult(copy(categoryIndex = (safeCategory + 1).coerceAtMost(lastCategory)))
            RemoteKey.LEFT -> SettingsNavigationResult(copy(categoryIndex = safeCategory), SettingsNavigationEffect.ExitToMainMenu)
            RemoteKey.RIGHT, RemoteKey.CONFIRM -> {
                val opened = SettingsSection.entries[safeCategory]
                SettingsNavigationResult(copy(section = opened, categoryIndex = safeCategory, option = opened.options().firstOrNull()))
            }
        }
    }

    val options = section.options()
    if (options.isEmpty()) return SettingsNavigationResult(this, handled = false)
    val index = options.indexOf(option).coerceAtLeast(0)
    val safeState = copy(option = options[index])
    return when (key) {
        RemoteKey.UP -> SettingsNavigationResult(safeState.copy(option = options[(index - 1).coerceAtLeast(0)]))
        RemoteKey.DOWN -> SettingsNavigationResult(safeState.copy(option = options[(index + 1).coerceAtMost(options.lastIndex)]))
        RemoteKey.LEFT -> SettingsNavigationResult(safeState, SettingsNavigationEffect.Adjust(options[index], -1))
        RemoteKey.RIGHT -> SettingsNavigationResult(safeState, SettingsNavigationEffect.Adjust(options[index], 1))
        RemoteKey.CONFIRM -> SettingsNavigationResult(safeState, SettingsNavigationEffect.Activate(options[index]))
    }
}
