package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.provider.CalendarProvider
import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.domain.model.calendar.CalendarType
import com.github.myeoungdev.marketticker.domain.model.calendar.MarketCalendarEvent
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service(Service.Level.APP)
class CalendarService(
    private val calendarProvider: CalendarProvider = DefaultDataSourceRegistry.calendarProvider()
) {

    private data class CacheEntry(val expiresAt: Long, val value: Any)

    private val cache = mutableMapOf<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<Any>>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun loadEvents(
        type: CalendarType,
        limit: Int = 50,
        forceRefresh: Boolean = false
    ): List<MarketCalendarEvent> {
        return cached("calendar:${type.name}:$limit", 300_000L, forceRefresh) {
            calendarProvider.getEvents(type, limit)
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
