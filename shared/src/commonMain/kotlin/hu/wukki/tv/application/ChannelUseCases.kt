package hu.wukki.tv

class SelectChannel(
    private val channels: ChannelRepository,
) {
    operator fun invoke(id: String): Channel? = channels.channels.firstOrNull { it.id == id }
}

class ToggleFavorite(
    private val channels: ChannelRepository,
) {
    operator fun invoke(id: String) = channels.toggleFavorite(id)
}

class UpdateSettings(
    private val repository: SettingsRepository,
) {
    operator fun invoke(transform: (AppSettings) -> AppSettings) = repository.update(transform)

    fun playback(transform: (PlaybackSettings) -> PlaybackSettings) =
        repository.update { current ->
            val playback = transform(current.playback)
            current.copy(playback = playback.copy(volume = playback.volume.coerceIn(0, 100), reconnectAttempts = playback.reconnectAttempts.coerceIn(1, 10)))
        }

    fun display(transform: (DisplaySettings) -> DisplaySettings) = repository.update { it.copy(display = transform(it.display)) }
}
