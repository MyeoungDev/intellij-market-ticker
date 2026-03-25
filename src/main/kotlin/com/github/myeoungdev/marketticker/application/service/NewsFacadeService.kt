package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.model.news.NewsHomeViewData
import com.github.myeoungdev.marketticker.application.model.news.TickerNewsSummaryViewData
import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.application.provider.NewsProvider
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 뉴스 화면과 하단 뉴스 요약 패널에서 사용하는 조합/캐시 계층입니다.
 */
@Service(Service.Level.APP)
class NewsFacadeService(
    private val newsProvider: NewsProvider = DefaultDataSourceRegistry.newsProvider()
) {

    private data class CacheEntry(val expiresAt: Long, val value: Any)

    private val cache = mutableMapOf<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<Any>>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun loadNewsHome(forceRefresh: Boolean = false): NewsHomeViewData {
        return cached("news-home:v1", 60_000L, forceRefresh) {
            NewsHomeViewData(
                headlines = newsProvider.getHeadlineNews(),
                mostViewed = newsProvider.getMostViewedNews(limit = 15)
            )
        }
    }

    suspend fun loadTickerNewsSummary(ticker: Ticker, forceRefresh: Boolean = false): TickerNewsSummaryViewData {
        val cacheKey = "ticker-news:${ticker.marketType.name}:${ticker.tradingSymbol}"
        val bundle = cached(cacheKey, 120_000L, forceRefresh) {
            newsProvider.getTickerNews(ticker)
        }

        val subtitle = when {
            bundle.articles.isNotEmpty() -> "최근 뉴스 ${bundle.articles.size}건"
            ticker.marketType.isCryptoMarket() && bundle.overviewCard != null -> "코인 개요만 표시합니다."
            else -> "최근 뉴스 없음"
        }

        return TickerNewsSummaryViewData(
            title = "${ticker.name} 뉴스",
            subtitle = subtitle,
            overviewCard = bundle.overviewCard,
            articles = bundle.articles,
            statusMessage = subtitle
        )
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
