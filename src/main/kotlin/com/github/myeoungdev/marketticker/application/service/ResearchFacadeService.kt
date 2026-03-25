package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.model.research.ResearchHomeViewData
import com.github.myeoungdev.marketticker.application.model.research.ResearchSummaryViewData
import com.github.myeoungdev.marketticker.application.model.research.StockResearchViewData
import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.application.provider.ResearchProvider
import com.github.myeoungdev.marketticker.application.provider.SearchProvider
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.research.ResearchArticle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingBundle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingType
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service(Service.Level.APP)
class ResearchFacadeService(
    private val researchProvider: ResearchProvider = DefaultDataSourceRegistry.researchProvider(),
    private val searchProvider: SearchProvider = DefaultDataSourceRegistry.searchProvider()
) {

    private data class CacheEntry(val expiresAt: Long, val value: Any)

    private val cache = mutableMapOf<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<Any>>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun loadResearchHome(forceRefresh: Boolean = false): ResearchHomeViewData {
        val latest = cached("research-home:latest", 300_000L, forceRefresh) {
            researchProvider.getCategoryLatestResearch()
        }
        val ranking = loadRankingBundle(ResearchRankingType.SEARCH_TOP, 1, forceRefresh)
        return ResearchHomeViewData(
            latestByCategory = latest,
            rankingArticles = ranking.latestResearch.map { it.withAnalyst(buildRankingMeta(ranking, 1)) }
        )
    }

    suspend fun loadRankingResearch(
        rankingType: ResearchRankingType,
        selectedRank: Int,
        forceRefresh: Boolean = false
    ): List<ResearchArticle> {
        val ranking = loadRankingBundle(rankingType, selectedRank, forceRefresh)
        val meta = buildRankingMeta(ranking, selectedRank)
        return ranking.latestResearch.map { it.copy(category = rankingType.label, analyst = meta) }
    }

    suspend fun loadStockResearch(
        query: String,
        preferredItemCode: String? = null,
        preferredName: String? = null,
        forceRefresh: Boolean = false
    ): StockResearchViewData {
        val resolved = resolveStock(query, preferredItemCode, preferredName)
            ?: return StockResearchViewData(
                resolvedTicker = null,
                articles = emptyList(),
                statusMessage = "NO_MATCH"
            )

        val articles = cached(
            key = "research-stock:${resolved.marketType.name}:${resolved.symbol}",
            ttlMillis = 600_000L,
            forceRefresh = forceRefresh
        ) {
            researchProvider.getStockResearch(resolved.symbol)
        }

        return StockResearchViewData(
            resolvedTicker = resolved,
            articles = articles,
            statusMessage = if (articles.isEmpty()) "EMPTY" else "OK"
        )
    }

    suspend fun searchStockSuggestions(query: String): List<Ticker> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 2) return emptyList()

        return cached("research-search:${normalizedQuery.lowercase()}", 30_000L, forceRefresh = false) {
            searchProvider.search(normalizedQuery)
                .filter { it.marketType.isKoreanMarket() }
                .take(8)
        }
    }

    suspend fun loadTickerResearchSummary(ticker: Ticker, forceRefresh: Boolean = false): ResearchSummaryViewData {
        val articles = cached(
            key = "research-summary:${ticker.marketType.name}:${ticker.symbol}",
            ttlMillis = 600_000L,
            forceRefresh = forceRefresh
        ) {
            if (!ticker.marketType.isKoreanMarket()) {
                emptyList()
            } else {
                researchProvider.getStockResearch(ticker.symbol, size = 3).take(3)
            }
        }

        return ResearchSummaryViewData(
            title = "${ticker.name} 리서치",
            statusMessage = when {
                !ticker.marketType.isKoreanMarket() -> "국내 종목 리서치만 지원합니다."
                articles.isEmpty() -> "최근 리서치 없음"
                else -> "최근 리서치 ${articles.size}건"
            },
            articles = articles
        )
    }

    private suspend fun loadRankingBundle(
        rankingType: ResearchRankingType,
        selectedRank: Int,
        forceRefresh: Boolean
    ): ResearchRankingBundle {
        return cached(
            key = "research-ranking:${rankingType.name}:$selectedRank",
            ttlMillis = 300_000L,
            forceRefresh = forceRefresh
        ) {
            researchProvider.getResearchRanking(rankingType, selectedRank)
        }
    }

    private suspend fun resolveStock(query: String, preferredItemCode: String?, preferredName: String?): Ticker? {
        preferredItemCode?.takeIf { it.isNotBlank() }?.let { code ->
            val exactMatch = searchProvider.search(code)
                .filter { it.marketType.isKoreanMarket() }
                .firstOrNull { it.symbol.equals(code, ignoreCase = true) }
            if (exactMatch != null) {
                return exactMatch
            }

            return Ticker(
                symbol = code,
                tradingSymbol = code,
                name = preferredName?.takeIf { it.isNotBlank() } ?: code,
                marketType = MarketType.UNKNOWN,
                nationCode = "KOR",
                nationName = "대한민국"
            )
        }

        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return null

        val results = searchStockSuggestions(normalizedQuery)
        return results.firstOrNull { it.symbol.equals(normalizedQuery, ignoreCase = true) }
            ?: results.firstOrNull { it.name.equals(normalizedQuery, ignoreCase = true) }
            ?: results.firstOrNull { it.name.contains(normalizedQuery, ignoreCase = true) }
            ?: results.firstOrNull()
    }

    private fun buildRankingMeta(bundle: ResearchRankingBundle, selectedRank: Int): String? {
        val item = bundle.ranking.getOrNull(selectedRank - 1) ?: return null
        val parts = listOfNotNull(
            "${selectedRank}위 ${item.itemName}",
            item.nowVal.takeIf { it.isNotBlank() }?.let { "현재가 $it" },
            item.changeRate.takeIf { it.isNotBlank() }?.let { "등락률 ${if (it.startsWith("-")) "" else "+"}$it%" },
            item.per.takeIf { it.isNotBlank() }?.let { "PER $it" },
            item.pbr.takeIf { it.isNotBlank() }?.let { "PBR $it" }
        )
        return parts.joinToString(" · ").ifBlank { null }
    }

    private fun ResearchArticle.withAnalyst(meta: String?): ResearchArticle {
        return if (meta.isNullOrBlank()) this else copy(analyst = meta)
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
