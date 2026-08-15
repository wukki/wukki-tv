package hu.wukki.tv.ui.guide

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.Programme

/** Inclusive-start, exclusive-end period drawn by the continuous EPG timeline. */
data class GuideTimeline(
    val start: Long,
    val end: Long
) {
    init {
        require(end > start) { "Guide timeline end must be after its start" }
    }
}

/** Immutable guide input. The guide's transient selection and scroll state stays in EpgGuideState. */
data class GuideUiState(
    val language: AppLanguage,
    val channels: List<Channel>,
    val now: Long
)

interface GuideDataSource {
    val language: AppLanguage
    val selectedChannelId: String?
    fun channels(): List<Channel>
    /** Latest end time of programmes belonging to the active playlist's matched channels. */
    fun latestProgrammeEnd(): Long?
    fun programmesFor(channel: Channel, from: Long, to: Long): List<Programme>
}
