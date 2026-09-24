package hu.wukki.tv.ui.components

import java.awt.Font

/** Use the same bundled outlines as Compose and webOS; never resolve a system alias. */
object OverlayFont {
    private val regular by lazy { load(400) }
    private val bold by lazy { load(700) }

    private fun load(weight: Int): Font =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fonts/inter_$weight.ttf")).use {
            Font.createFont(Font.TRUETYPE_FONT, it)
        }

    fun at(
        style: Int,
        size: Int,
    ): Font = (if (style == Font.BOLD) bold else regular).deriveFont(size.toFloat())
}
