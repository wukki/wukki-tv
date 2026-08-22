package hu.wukki.tv.ui.app

import hu.wukki.tv.Channel
import hu.wukki.tv.WukkiModel
import hu.wukki.tv.ui.guide.GuideDataSource

/** App-layer adapter that keeps the Guide feature independent from [WukkiModel]. */
internal fun WukkiModel.guideDataSource(): GuideDataSource = object : GuideDataSource {
    override val language get() = settings.language
    override val selectedChannelId get() = this@guideDataSource.selectedChannelId
    override fun channels() = guideChannels()
    override fun latestProgrammeEnd() = guideLatestProgrammeEnd()
    override fun programmesFor(channel: Channel, from: Long, to: Long) =
        this@guideDataSource.programmesFor(channel, from, to)
}
