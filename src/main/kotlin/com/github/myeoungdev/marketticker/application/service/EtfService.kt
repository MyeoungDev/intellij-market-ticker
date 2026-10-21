package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.application.provider.EtfProvider
import com.github.myeoungdev.marketticker.domain.model.etf.EtfDetail
import com.github.myeoungdev.marketticker.domain.model.etf.EtfSummary
import com.github.myeoungdev.marketticker.domain.model.etf.EtfDistributionHistory
import com.intellij.openapi.components.Service
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap

@Service(Service.Level.APP)
class EtfService(private val provider: EtfProvider = DefaultDataSourceRegistry.etfProvider()) {
    companion object {
        private const val CACHE_TTL_MS = 60_000L
        private const val MAX_CACHED_PAGES = 20
        private const val MAX_CACHED_DETAILS = 32
    }

    private val mutex = Mutex()
    private val listCache = LinkedHashMap<Int, Pair<Long, List<EtfSummary>>>(16, 0.75f, true)
    private val detailCache = LinkedHashMap<String, Pair<Long, EtfDetail?>>(16, 0.75f, true)
    private val distributionCache = LinkedHashMap<String, Pair<Long, EtfDistributionHistory>>(16, 0.75f, true)

    suspend fun loadEtfs(force: Boolean = false): List<EtfSummary> {
        return loadPage(0, force)
    }

    suspend fun loadPage(page: Int, force: Boolean = false): List<EtfSummary> {
        val now = System.currentTimeMillis()
        mutex.withLock {
            evictExpired(listCache, now)
            listCache[page]?.takeIf { !force && it.first > now }?.second
        }?.let { return it }
        val values = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { provider.getDomesticEtfs(page, 100) }
        mutex.withLock {
            val storedAt = System.currentTimeMillis()
            evictExpired(listCache, storedAt)
            listCache[page] = storedAt + CACHE_TTL_MS to values
            trimOldest(listCache, MAX_CACHED_PAGES)
        }
        return values
    }

    suspend fun loadDetail(code: String, force: Boolean = false): EtfDetail? {
        val cached = mutex.withLock {
            val now = System.currentTimeMillis()
            evictExpired(detailCache, now)
            detailCache[code]?.takeIf { !force && it.first > now }?.second
        }
        if (cached != null) return cached
        val value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { provider.getDomesticEtfDetail(code) }
        mutex.withLock {
            val storedAt = System.currentTimeMillis()
            evictExpired(detailCache, storedAt)
            detailCache[code] = storedAt + CACHE_TTL_MS to value
            trimOldest(detailCache, MAX_CACHED_DETAILS)
        }
        return value
    }

    suspend fun loadDistributions(code: String): EtfDistributionHistory {
        val cached = mutex.withLock {
            val now = System.currentTimeMillis()
            evictExpired(distributionCache, now)
            distributionCache[code]?.takeIf { it.first > now }?.second
        }
        if (cached != null) return cached
        val value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { provider.getDomesticEtfDistributions(code) }
        mutex.withLock {
            val storedAt = System.currentTimeMillis()
            evictExpired(distributionCache, storedAt)
            distributionCache[code] = storedAt + CACHE_TTL_MS to value
            trimOldest(distributionCache, MAX_CACHED_DETAILS)
        }
        return value
    }

    private fun <K, V> evictExpired(cache: MutableMap<K, Pair<Long, V>>, now: Long) {
        cache.entries.removeIf { it.value.first <= now }
    }

    private fun <K, V> trimOldest(cache: LinkedHashMap<K, V>, limit: Int) {
        while (cache.size > limit) cache.remove(cache.keys.first())
    }
}
