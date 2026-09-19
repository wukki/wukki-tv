package hu.wukki.tv.ui.components

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.OTHER_CATEGORY_ID

/** Translate display labels only: playlist group values remain the category identities. */
fun String.displayCategoryName(language: AppLanguage): String {
    val key =
        when (trim().lowercase()) {
            "comedy", "vígjáték" -> "channels.category.comedy"
            "culture", "kultúra" -> "channels.category.culture"
            "documentary", "documentaries", "dokumentum", "dokumentumfilm", "dokumentumfilmek" -> "channels.category.documentary"
            "education", "oktatás" -> "channels.category.education"
            "entertainment", "szórakozás", "szórakoztató" -> "channels.category.entertainment"
            "general", "általános" -> "channels.category.general"
            "kids", "children", "gyerek", "gyermek", "gyerekek" -> "channels.category.kids"
            "movies", "movie", "film", "filmek" -> "channels.category.movies"
            "music", "zene" -> "channels.category.music"
            "news", "hírek" -> "channels.category.news"
            "outdoor", "szabadidő" -> "channels.category.outdoor"
            "sports", "sport" -> "channels.category.sports"
            OTHER_CATEGORY_ID, "other", "egyéb", "" -> "channels.other"
            else -> return this
        }
    return tr(language, key)
}
