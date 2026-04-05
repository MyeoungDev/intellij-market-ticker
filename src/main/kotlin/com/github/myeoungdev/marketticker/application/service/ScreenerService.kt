package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.application.provider.ScreenerProvider
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenedTicker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenerPreset
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service(Service.Level.APP)
class ScreenerService(
    private val screenerProvider: ScreenerProvider = DefaultDataSourceRegistry.screenerProvider()
) {

    private data class CacheEntry(val expiresAt: Long, val value: Any)

    private val cache = mutableMapOf<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<Any>>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun loadScreen(
        preset: ScreenerPreset,
        limit: Int = 25,
        forceRefresh: Boolean = false
    ): List<ScreenedTicker> {
        return cached("screen:${preset.name}:$limit", 60_000L, forceRefresh) {
            screenerProvider.getScreen(preset, limit)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(
        key: String,
        ttlMillis: Long,
        forceRefresh: Boolean,
        loader: () -> T
    ): T {
        val now = System.currentTimeMillis()
        val deferred = mutex.withLock {
            val entry = cache[key]
            if (!forceRefresh && entry != null && entry.expiresAt > now) {
                return entry.value as T
            }
            val existing = inFlight[key]
            if (existing != null) {
                return@withLock existing
            }

            val newDeferred = CompletableDeferred<Any>()
            inFlight[key] = newDeferred
            scope.launch {
                try {
                    newDeferred.complete(loader() as Any)
                } catch (t: Throwable) {
                    newDeferred.completeExceptionally(t)
                }
            }
            newDeferred
        }

        return try {
            val value = deferred.await()
            mutex.withLock {
                cache[key] = CacheEntry(System.currentTimeMillis() + ttlMillis, value)
                inFlight.remove(key)
            }
            value as T
        } catch (t: Throwable) {
            mutex.withLock {
                inFlight.remove(key)
            }
            throw t
        }
    }
}
