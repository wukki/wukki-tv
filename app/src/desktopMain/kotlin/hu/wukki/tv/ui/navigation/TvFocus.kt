package hu.wukki.tv.ui.navigation

import androidx.compose.ui.input.key.Key

/** High-level TV remote focus zones. Individual screens keep their own item selection. */
enum class TvFocusZone { MAIN_NAVIGATION, CONTENT }

enum class ChannelRemoteFocus { FILTERS, SEARCH, LIST, FAVORITE }

fun Key.isConfirmKey(): Boolean = this == Key.Enter || this == Key.NumPadEnter
fun Key.isBackKey(): Boolean = this == Key.Escape || this == Key.Backspace
