package hu.wukki.tv

/** Application failures contain no localized text or translation-key conventions. */
sealed interface AppFailure {
    data object NetworkUnavailable : AppFailure

    data class HttpError(
        val status: Int,
    ) : AppFailure

    data object InvalidPlaylist : AppFailure

    data object InvalidXmlTv : AppFailure

    data object MissingEpgSource : AppFailure

    data object InvalidRemoteUrl : AppFailure

    data object ResponseTooLarge : AppFailure

    data object Unknown : AppFailure
}

open class AppOperationException(
    val failure: AppFailure,
    cause: Throwable? = null,
) : IllegalArgumentException(failure.toString(), cause)

sealed interface RefreshEvent {
    data object PlaylistLoading : RefreshEvent

    data class PlaylistLoaded(
        val count: Int,
    ) : RefreshEvent

    data class EpgLoading(
        val sourceName: String,
    ) : RefreshEvent

    data class EpgLoaded(
        val sourceName: String,
        val count: Int,
    ) : RefreshEvent

    data class Failed(
        val failure: AppFailure,
        val sourceName: String? = null,
        val playlistUnavailable: Boolean = false,
    ) : RefreshEvent
}
