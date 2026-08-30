package hu.wukki.tv.ui.components

import hu.wukki.tv.AppLanguage
import java.text.MessageFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.ResourceBundle

actual fun platformText(language: AppLanguage, key: String, arguments: List<Any?>): String {
    val locale = language.locale()
    val pattern = runCatching { ResourceBundle.getBundle("i18n.messages", locale).getString(key) }.getOrElse { "⟪$key⟫" }
    return if (arguments.isEmpty()) pattern else MessageFormat(pattern, locale).format(arguments.toTypedArray())
}

actual fun platformTimeLabel(millis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

actual fun platformDateLabel(language: AppLanguage, millis: Long, pattern: String): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, language.locale()))

actual fun platformTextResource(path: String): String? = PlatformLocalizationAnchor::class.java.classLoader
    ?.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

actual fun platformIsStartOfDay(millis: Long): Boolean {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return time.hour == 0 && time.minute == 0
}

actual fun platformStartOfDay(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

actual fun platformStartOfNextDay(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

actual fun platformGuideDateLabel(language: AppLanguage, millis: Long, pattern: String): String =
    platformDateLabel(language, millis, pattern)

private fun AppLanguage.locale(): Locale = if (this == AppLanguage.HUNGARIAN) Locale.forLanguageTag("hu") else Locale.ENGLISH
private object PlatformLocalizationAnchor
