package hu.wukki.tv.ui.navigation

data class ExitConfirmationState(val firstBackAtMillis: Long? = null)

enum class ExitConfirmationEffect { SHOW_HINT, EXIT }

data class ExitConfirmationResult(
    val state: ExitConfirmationState,
    val effect: ExitConfirmationEffect
)

fun ExitConfirmationState.requestExit(
    nowMillis: Long,
    confirmationWindowMillis: Long = DEFAULT_EXIT_CONFIRMATION_WINDOW_MS
): ExitConfirmationResult {
    val firstBack = firstBackAtMillis
    return if (firstBack != null && nowMillis - firstBack in 0..confirmationWindowMillis) {
        ExitConfirmationResult(ExitConfirmationState(), ExitConfirmationEffect.EXIT)
    } else {
        ExitConfirmationResult(ExitConfirmationState(nowMillis), ExitConfirmationEffect.SHOW_HINT)
    }
}

internal const val DEFAULT_EXIT_CONFIRMATION_WINDOW_MS = 2_000L
