package hu.wukki.tv.webos

internal enum class LiveLayer {
    NAVIGATION,
    INFORMATION_PANEL,
    CHANNEL_NUMBER,
    DIALOG,
}

internal fun liveLayerTimeoutMillis(layer: LiveLayer): Int? =
    when (layer) {
        LiveLayer.NAVIGATION,
        LiveLayer.INFORMATION_PANEL,
        -> 5_000

        LiveLayer.CHANNEL_NUMBER -> 3_000

        LiveLayer.DIALOG -> null
    }
