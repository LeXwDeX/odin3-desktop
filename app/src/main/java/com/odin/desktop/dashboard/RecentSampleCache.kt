package com.odin.desktop.dashboard

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A visit-owned loader with a repository-owned, bounded lifetime for complete samples. */
internal class RecentSampleCache<K, V : Any>(
    private val ttlMillis: Long,
    private val monotonicMillis: () -> Long
) {
    init { require(ttlMillis > 0) }

    private data class Entry<K, V>(val key: K, val value: V, val completedAt: Long)
    private val lock = Mutex()
    private var entry: Entry<K, V>? = null

    suspend fun sample(key: () -> K, loader: suspend (K) -> V?): V? = lock.withLock {
        currentCoroutineContext().ensureActive()
        val currentKey = key()
        val now = monotonicMillis()
        entry?.takeIf {
            it.key == currentKey && now >= it.completedAt && now - it.completedAt < ttlMillis
        }?.let { return@withLock it.value }
        // Expiry, failed reads and permission changes never keep an old success alive.
        entry = null
        val value = loader(currentKey)
        currentCoroutineContext().ensureActive()
        if (key() != currentKey) return@withLock null
        if (value != null) entry = Entry(currentKey, value, monotonicMillis())
        value
    }

    suspend fun invalidate() = lock.withLock { entry = null }
}
