package hu.wukki.tv.ui.guide

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.Programme

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
    fun programmesFor(channel: Channel, from: Long, to: Long): List<Programme>
}
