package hu.wukki.tv.ui.components

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Programme
import hu.wukki.tv.UserMessage
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

object Localizer {
    fun text(language: AppLanguage, key: String, vararg arguments: Any?): String {
        val bundle = ResourceBundle.getBundle("i18n.messages", language.locale)
        val pattern = runCatching { bundle.getString(key) }.getOrElse { "⟪$key⟫" }
        return if (arguments.isEmpty()) pattern else MessageFormat(pattern, language.locale).format(arguments)
    }

    fun locale(language: AppLanguage): Locale = language.locale
    private val AppLanguage.locale get() = if (this == AppLanguage.HUNGARIAN) Locale.forLanguageTag("hu") else Locale.ENGLISH
}

fun tr(language: AppLanguage, key: String, vararg arguments: Any?): String = Localizer.text(language, key, *arguments)

fun Programme.displayTitle(language: AppLanguage): String = title.ifBlank { tr(language, "epg.untitled") }

fun UserMessage.text(language: AppLanguage): String = when (this) {
    is hu.wukki.tv.UserMessage.Key -> tr(
        language,
        key,
        *arguments.map { argument -> (argument as? UserMessage)?.text(language) ?: argument }.toTypedArray()
    )
    is hu.wukki.tv.UserMessage.Raw -> value
}
