package hu.wukki.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-owned work: concurrent requests join one result, and different refreshes serialize. */
class RefreshService(private val scope: CoroutineScope) {
    private val requests = Mutex()
    private val writer = Mutex()
    private val active = mutableMapOf<String, Deferred<Boolean>>()

    suspend fun refresh(key: String, action: suspend () -> Boolean): Boolean {
        val job = requests.withLock {
            active[key]?.takeUnless { it.isCompleted } ?: scope.async(start = CoroutineStart.LAZY) {
                writer.withLock { action() }
            }.also { active[key] = it }
        }
        // The service owns the job, so destroying the Activity or cancelling a waiter cannot cancel it.
        return job.await()
    }
}
