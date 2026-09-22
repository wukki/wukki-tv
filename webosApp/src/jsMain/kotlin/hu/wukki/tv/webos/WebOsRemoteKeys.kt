package hu.wukki.tv.webos

import hu.wukki.tv.ui.navigation.AppRemoteKey
import hu.wukki.tv.ui.navigation.LiveChannelPreviewEvent
import hu.wukki.tv.ui.navigation.RemoteKey

internal const val WEBOS_BACK_KEY = 461
internal const val WEBOS_RED_KEY = 403
internal const val WEBOS_GREEN_KEY = 404

internal fun webOsRemoteKey(
    keyCode: Int,
    liveContent: Boolean,
    repeated: Boolean = false,
): AppRemoteKey? {
    val remote = directionalRemoteKey(keyCode)
    if (repeated && remote == RemoteKey.CONFIRM) return null
    return when {
        remote != null -> {
            AppRemoteKey(
                remote = remote,
                preview =
                    if (liveContent) {
                        when (remote) {
                            RemoteKey.UP -> LiveChannelPreviewEvent.NEXT
                            RemoteKey.DOWN -> LiveChannelPreviewEvent.PREVIOUS
                            else -> null
                        }
                    } else {
                        null
                    },
            )
        }

        else -> {
            specialRemoteKey(keyCode)
        }
    }
}

private fun directionalRemoteKey(keyCode: Int): RemoteKey? =
    when (keyCode) {
        37 -> RemoteKey.LEFT
        38 -> RemoteKey.UP
        39 -> RemoteKey.RIGHT
        40 -> RemoteKey.DOWN
        13 -> RemoteKey.CONFIRM
        else -> null
    }

private fun specialRemoteKey(keyCode: Int): AppRemoteKey? =
    when (keyCode) {
        WEBOS_BACK_KEY -> AppRemoteKey(back = true)
        27 -> AppRemoteKey(back = true, escape = true)
        8 -> AppRemoteKey(backspace = true)
        33 -> AppRemoteKey(channelDelta = 1)
        34 -> AppRemoteKey(channelDelta = -1)
        WEBOS_RED_KEY -> AppRemoteKey(previousChannel = true)
        WEBOS_GREEN_KEY -> AppRemoteKey(quickSettings = true)
        in 48..57 -> AppRemoteKey(digit = (keyCode - 48).toString())
        else -> null
    }
