package hu.wukki.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One in-flight write and one latest pending snapshot, regardless of UI input frequency. */
class StateWriter(
    scope: CoroutineScope,
    private val store: AppStateStore,
    private val coalesceMillis: Long = 200,
) {
    private data class Request(
        val revision: Long,
        val state: AppState,
    )

    private val pending = MutableStateFlow<Request?>(null)
    private val writing = Mutex()
    private val mutableFailure = MutableStateFlow<Exception?>(null)
    val failure = mutableFailure.asStateFlow()
    private var attemptedRevision = 0L
    private var savedRevision = 0L

    init {
        scope.launch {
            pending.collect { request ->
                if (request != null) {
                    delay(coalesceMillis)
                    writeLatest(retry = false)
                }
            }
        }
    }

    fun submit(state: AppState) {
        pending.update { previous -> Request((previous?.revision ?: 0) + 1, state) }
    }

    /** Bypasses the delay, retries failed saves and waits for all changes submitted before this call. */
    suspend fun flush() {
        writeLatest(retry = true)?.let { throw it }
    }

    private suspend fun writeLatest(retry: Boolean): Exception? =
        writing.withLock {
            val request = pending.value ?: return@withLock null
            if (request.revision <= savedRevision) return@withLock null
            if (!retry && request.revision <= attemptedRevision) return@withLock mutableFailure.value
            attemptedRevision = request.revision
            try {
                store.save(request.state)
                savedRevision = request.revision
                mutableFailure.value = null
                null
            } catch (exception: CancellationException) {
                attemptedRevision = savedRevision
                throw exception
            } catch (exception: Exception) {
                mutableFailure.value = exception
                exception
            }
        }
}
