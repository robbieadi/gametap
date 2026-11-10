package io.github.robbieadi.gametap

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory DataStore. Like the real one, it applies updates one at a time. */
class InMemoryDataStore<T>(initial: T) : DataStore<T> {
    private val mutex = Mutex()
    private val current = MutableStateFlow(initial)
    override val data: Flow<T> = current

    val value: T get() = current.value

    override suspend fun updateData(transform: suspend (t: T) -> T): T =
        mutex.withLock { transform(current.value).also { current.value = it } }
}
