package hu.wukki.tv.ui.components

import hu.wukki.tv.AppLanguage

fun localized(language: AppLanguage, hungarian: String, english: String): String =
    if (language == AppLanguage.HUNGARIAN) hungarian else english
