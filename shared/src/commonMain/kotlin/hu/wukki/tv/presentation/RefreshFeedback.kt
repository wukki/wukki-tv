package hu.wukki.tv

/** The application layer emits typed events; only presentation knows localization keys. */
internal fun RefreshEvent.feedback(): Pair<AppFeedbackKind, UserMessage> =
    when (this) {
        RefreshEvent.PlaylistLoading -> {
            AppFeedbackKind.LOADING to UserMessage.Key("status.playlist.refreshing", listOf(OfficialWukkiSource.PLAYLIST_NAME))
        }

        is RefreshEvent.PlaylistLoaded -> {
            AppFeedbackKind.SUCCESS to UserMessage.Key("status.playlist.refreshed", listOf(count))
        }

        is RefreshEvent.EpgLoading -> {
            AppFeedbackKind.LOADING to UserMessage.Key("status.epg.loading", listOf(sourceName))
        }

        is RefreshEvent.EpgLoaded -> {
            AppFeedbackKind.SUCCESS to UserMessage.Key("status.epg.loaded", listOf(count, sourceName))
        }

        is RefreshEvent.Failed -> {
            AppFeedbackKind.ERROR to
                when {
                    failure == AppFailure.MissingEpgSource -> UserMessage.Key("error.wukki.epg.missing")
                    sourceName != null -> UserMessage.Key("error.epg.load", listOf(sourceName, failure.userMessage()))
                    playlistUnavailable -> UserMessage.Key("error.wukki.playlist.unavailable", listOf(failure.userMessage()))
                    else -> UserMessage.Key("error.playlist.refresh", listOf(failure.userMessage()))
                }
        }
    }

internal fun AppFailure.userMessage(): UserMessage =
    when (this) {
        AppFailure.NetworkUnavailable -> UserMessage.Key("error.network.unavailable")
        is AppFailure.HttpError -> UserMessage.Key("error.network.http", listOf(status))
        AppFailure.InvalidPlaylist -> UserMessage.Key("error.playlist.empty")
        AppFailure.InvalidXmlTv -> UserMessage.Key("error.epg.empty")
        AppFailure.MissingEpgSource -> UserMessage.Key("error.wukki.epg.missing")
        AppFailure.InvalidRemoteUrl -> UserMessage.Key("error.network.url")
        AppFailure.ResponseTooLarge -> UserMessage.Key("error.network.too.large")
        AppFailure.Unknown -> UserMessage.Key("error.unknown")
    }
