package hu.wukki.tv.ui.layout

/** Logical viewport contract, independent of the operating system and input device. */
object DisplayLayout {
    const val WIDE_MIN_WIDTH = 960f
    const val NAVIGATION_HEIGHT = 48f
    const val SETTINGS_CATEGORY_WIDTH = 430f
    const val SETTINGS_PANEL_GAP = 34f

    fun isCompact(width: Float): Boolean = width < WIDE_MIN_WIDTH

    fun dashboardScale(
        width: Float,
        height: Float,
    ): Float = minOf(width / 1470f, height / 920f).coerceIn(.70f, 1.45f)

    fun settingsScale(
        width: Float,
        height: Float,
    ): Float = minOf(width / 1116f, height / 892f).coerceIn(.70f, 1f)
}
