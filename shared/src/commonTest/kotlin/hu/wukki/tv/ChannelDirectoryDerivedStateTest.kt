package hu.wukki.tv

import hu.wukki.tv.ui.components.displayCategoryName
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelDirectoryDerivedStateTest {
    @Test
    fun `language changes labels while category identities order and filtering stay unchanged`() {
        val model =
            model(
                channel("english", "English group", "Sports", number = 1),
                channel("hungarian", "Hungarian group", "Sport", number = 2),
                channel("news", "News channel", "News", number = 3),
            )
        model.showChannelCategory("Sports")
        val categories = model.categories()
        val groups = model.guideChannels().map(Channel::group)

        listOf(AppLanguage.HUNGARIAN, AppLanguage.ENGLISH, AppLanguage.HUNGARIAN).forEach { language ->
            model.setLanguage(language)
            assertEquals(language, model.settings.language)
            assertEquals("Sports", model.category)
            assertEquals(categories, model.categories())
            assertEquals(groups, model.guideChannels().map(Channel::group))
            assertEquals(listOf("english"), model.filteredChannels().map(Channel::id))
            assertEquals(
                if (language == AppLanguage.HUNGARIAN) "Sport" else "Sports",
                model.category!!.displayCategoryName(model.settings.language),
            )
        }
    }

    @Test
    fun `query category and favorite filters derive from current model state`() {
        val model =
            model(
                channel("alpha", "Árvíztűrő TV", "News", favorite = true, number = 2),
                channel("beta", "Beta Sport", "Sports", number = 1),
                channel("gamma", "Gamma", "News", favorite = true, number = 3),
            )

        assertEquals(listOf("beta", "alpha", "gamma"), model.filteredChannels().map(Channel::id))
        assertEquals(listOf("News", "Sports"), model.categories())

        model.setChannelQuery("arvizturo")
        assertEquals(listOf("alpha"), model.filteredChannels().map(Channel::id))

        model.setChannelQuery("")
        model.showChannelCategory("Sports")
        assertEquals(listOf("beta"), model.filteredChannels().map(Channel::id))

        model.showFavoriteChannels()
        assertEquals(listOf("alpha", "gamma"), model.filteredChannels().map(Channel::id))

        model.toggleFavorite("beta")
        assertEquals(listOf("beta", "alpha", "gamma"), model.filteredChannels().map(Channel::id))
    }

    @Test
    fun `guide channels stay independent from directory filters`() {
        val model =
            model(
                channel("alpha", "Alpha", "News", favorite = true, number = 2),
                channel("beta", "Beta", "Sports", number = 1),
            )

        model.showFavoriteChannels()
        model.setChannelQuery("missing")

        assertEquals(emptyList(), model.filteredChannels())
        assertEquals(listOf("beta", "alpha"), model.guideChannels().map(Channel::id))
    }

    private fun model(vararg channels: Channel): WukkiModel =
        WukkiModel(
            initialState =
                AppState(
                    playlists =
                        listOf(
                            PlaylistDefinition(
                                id = "cached-official",
                                name = "Wukki TV",
                                location = OfficialWukkiSource.PLAYLIST_URL,
                                source = PlaylistSource.URL,
                                updatedAt = 1L,
                            ),
                        ),
                    channels = channels.map { it.copy(playlistId = "cached-official") },
                ),
            sourceLoader = RemoteTextLoader { error("No network expected") },
            xmlTvParser = XmlTvParser { emptyList() },
            stateSaver = {},
        )

    private fun channel(
        id: String,
        name: String,
        group: String,
        favorite: Boolean = false,
        number: Int,
    ) = Channel(
        id = id,
        playlistId = "cached-official",
        name = name,
        streamUrl = "https://example.test/$id.m3u8",
        tvgId = id,
        tvgName = name,
        tvgChno = number,
        group = group,
        logo = null,
        favorite = favorite,
    )
}
