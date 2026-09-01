package hu.wukki.tv.ui.navigation

import androidx.compose.ui.input.key.Key

/** High-level TV remote focus zones. Individual screens keep their own item selection. */
enum class TvFocusZone { MAIN_NAVIGATION, CONTENT }

enum class ChannelRemoteFocus { FILTERS, SEARCH, LIST, FAVORITE }

fun Key.isConfirmKey(): Boolean = this == Key.Enter || this == Key.NumPadEnter || this == Key.DirectionCenter
fun Key.isBackKey(): Boolean = this == Key.Escape || this == Key.Backspace || this == Key.Back

fun Key.toRemoteKey(): RemoteKey? = when (this) {
    Key.DirectionUp, Key.PageUp -> RemoteKey.UP
    Key.DirectionDown, Key.PageDown -> RemoteKey.DOWN
    Key.DirectionLeft -> RemoteKey.LEFT
    Key.DirectionRight -> RemoteKey.RIGHT
    else -> RemoteKey.CONFIRM.takeIf { isConfirmKey() }
}

/** Direction keys change the live stream immediately while the video content has focus. */
fun Key.liveImmediateChannelDelta(): Int? = when (this) {
    Key.DirectionUp -> 1
    Key.DirectionDown -> -1
    else -> null
}

/** Channel keys browse the information panel without changing the active stream. */
fun Key.livePreviewEvent(): LiveChannelPreviewEvent? = when (this) {
    Key.PageUp, Key.ChannelUp -> LiveChannelPreviewEvent.NEXT
    Key.PageDown, Key.ChannelDown -> LiveChannelPreviewEvent.PREVIOUS
    else -> null
}
