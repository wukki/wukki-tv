package hu.wukki.tv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One state owner for all repositories. Mutations and observers run on the application's owner
 * dispatcher (currently Main); only download/parse work leaves it. Each transform sees the latest
 * state, including edits made while a refresh was suspended. No Compose dependency lives here.
 */
class ApplicationStore(
    initialState: AppState,
    private val save: (AppState) -> Unit,
    clock: Clock = SystemClock,
) {
    private val mutableState = MutableStateFlow(OfficialWukkiSource.provision(initialState, clock.nowMillis()))
    val states = mutableState.asStateFlow()
    val current: AppState get() = states.value
    private val observers = mutableSetOf<(AppState) -> Unit>()

    init {
        if (current != initialState) save(current)
    }

    fun update(transform: (AppState) -> AppState) {
        val next = transform(current)
        if (next == current) return
        mutableState.value = next
        save(next)
        observers.toList().forEach { it(next) }
    }

    /** Synchronous adapter for the existing Compose façade; StateFlow is available to other clients. */
    fun observe(observer: (AppState) -> Unit): () -> Unit {
        observers += observer
        observer(current)
        return { observers -= observer }
    }
}
