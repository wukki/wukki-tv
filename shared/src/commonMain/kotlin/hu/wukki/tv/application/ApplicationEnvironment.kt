package hu.wukki.tv

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long =
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
}

/** Platform roots may use a dedicated IO pool; tests supply deterministic dispatchers. */
data class DispatcherProvider(
    val io: CoroutineDispatcher = Dispatchers.Default,
    val computation: CoroutineDispatcher = Dispatchers.Default,
)
