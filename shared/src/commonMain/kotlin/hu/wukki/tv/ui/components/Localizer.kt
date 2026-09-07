package hu.wukki.tv.ui.components

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.OTHER_CATEGORY_ID
import hu.wukki.tv.Programme
import hu.wukki.tv.UNKNOWN_CHANNEL_NAME_ID
import hu.wukki.tv.UserMessage

/** Common localization facade; bundle loading stays in platform source sets. */
object Localizer {
    fun text(language: AppLanguage, key: String, vararg arguments: Any?): String =
        platformText(language, key, arguments.toList())

    fun formatTime(millis: Long): String = platformTimeLabel(millis)
    fun legalText(path: String): String? = platformTextResource(path)
}

fun tr(language: AppLanguage, key: String, vararg arguments: Any?): String = Localizer.text(language, key, *arguments)
fun Programme.displayTitle(language: AppLanguage): String = title.ifBlank { tr(language, "epg.untitled") }
fun Channel.displayName(language: AppLanguage): String =
    if (name == UNKNOWN_CHANNEL_NAME_ID) tr(language, "channels.unknown") else name
fun String.displayCategoryName(language: AppLanguage): String =
    if (this == OTHER_CATEGORY_ID) tr(language, "channels.other") else this
fun UserMessage.text(language: AppLanguage): String = when (this) {
    is hu.wukki.tv.UserMessage.Key -> tr(language, key, *arguments.map { argument -> (argument as? UserMessage)?.text(language) ?: argument }.toTypedArray())
    is hu.wukki.tv.UserMessage.Raw -> value
}

expect fun platformText(language: AppLanguage, key: String, arguments: List<Any?>): String
expect fun platformTimeLabel(millis: Long): String
expect fun platformDateLabel(language: AppLanguage, millis: Long, pattern: String): String
expect fun platformTextResource(path: String): String?
expect fun platformIsStartOfDay(millis: Long): Boolean
expect fun platformStartOfDay(millis: Long): Long
expect fun platformStartOfNextDay(millis: Long): Long
expect fun platformGuideDateLabel(language: AppLanguage, millis: Long, pattern: String): String
