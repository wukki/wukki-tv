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

/** Dedicated channel keys change the live stream immediately. */
fun Key.liveImmediateChannelDelta(): Int? = when (this) {
    Key.PageUp, Key.ChannelUp -> 1
    Key.PageDown, Key.ChannelDown -> -1
    else -> null
}

/** Direction keys browse the information panel without changing the active stream. */
fun Key.livePreviewEvent(): LiveChannelPreviewEvent? = when (this) {
    Key.DirectionUp -> LiveChannelPreviewEvent.NEXT
    Key.DirectionDown -> LiveChannelPreviewEvent.PREVIOUS
    else -> null
}

/** Translation only: no screen-specific routing belongs in the platform adapter. */
fun Key.toAppRemoteKey(): AppRemoteKey = AppRemoteKey(
    remote = toRemoteKey(), back = isBackKey(), escape = this == Key.Escape,
    backspace = this == Key.Backspace, channelDelta = liveImmediateChannelDelta(), preview = livePreviewEvent(),
    digit = when (this) {
        Key.Zero, Key.NumPad0 -> "0"
        Key.One, Key.NumPad1 -> "1"
        Key.Two, Key.NumPad2 -> "2"
        Key.Three, Key.NumPad3 -> "3"
        Key.Four, Key.NumPad4 -> "4"
        Key.Five, Key.NumPad5 -> "5"
        Key.Six, Key.NumPad6 -> "6"
        Key.Seven, Key.NumPad7 -> "7"
        Key.Eight, Key.NumPad8 -> "8"
        Key.Nine, Key.NumPad9 -> "9"
        else -> null
    }
)
