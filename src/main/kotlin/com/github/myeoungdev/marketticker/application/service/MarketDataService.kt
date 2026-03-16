package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.listener.TickerUpdateListener
import com.github.myeoungdev.marketticker.application.listener.WatchlistEntryUpdateListener
import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.application.provider.PriceProvider
import com.github.myeoungdev.marketticker.application.provider.SearchProvider
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/**
 * MarketTicker 의 데이터르 관리하는 메인 서비스 클래스 입니다.
 *
 * @author : 강명관
 * @since : 2026-01-20
 */
@Service(Service.Level.APP)
class MarketDataService(
    private val cs: CoroutineScope,
) {

    private data class SearchCacheEntry(
        val expiresAt: Long,
        val results: List<Ticker>
    )

    private val watchlistRepository = service<WatchlistRepository>()
    private val notificationService = service<NotificationService>()
    private val priceHistoryService = service<PriceHistoryService>()

    private val priceProvider: PriceProvider = DefaultDataSourceRegistry.priceProvider()
    private val searchProvider: SearchProvider = DefaultDataSourceRegistry.searchProvider()

    private val _currentPrices = MutableStateFlow<List<TickerPrice>>(emptyList())
    val currentPrices: StateFlow<List<TickerPrice>> = _currentPrices.asStateFlow()
    private val refreshMutex = Mutex()
    private val searchMutex = Mutex()
    private val searchCache = mutableMapOf<String, SearchCacheEntry>()

    /**
     * 등록된 Ticker 의 실시간 가격을 갱신하는 메서드 입니다.
     */
    suspend fun refreshPrices() {
        refreshMutex.withLock {
            val watchlistEntries = watchlistRepository.getWatchlistEntries()

            if (watchlistEntries.isEmpty()) {
                _currentPrices.emit(emptyList())
                broadcastUpdate(emptyList())
                return
            }

            try {
                val tickersForPriceProvider = watchlistEntries.map { entry ->
                    Ticker(
                        entry.symbol,
                        entry.tradingSymbol.ifBlank { entry.symbol },
                        entry.name,
                        MarketType.of(entry.marketType),
                        entry.nationCode,
                        entry.nationName
                    )
                }

                val prices = withContext(Dispatchers.IO) {
                    priceProvider.getPrices(tickersForPriceProvider)
                }
                logger.info { "Fetched prices count: ${prices.size}" }

                _currentPrices.emit(prices)
                priceHistoryService.append(prices)
                notificationService.checkAndNotify(prices)
                broadcastUpdate(prices)
            } catch (e: Exception) {
                logger.error(e) { "Failed to refresh prices" }
            }
        }
    }

    /**
     * 현재 열려있는 프로젝트에 가격을 전파하는 메서드 입니다.
     *
     * @param prices 실시간 가격
     */
    private fun broadcastUpdate(prices: List<TickerPrice>) {
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(TickerUpdateListener.TOPIC).onTickerUpdated(prices)
            }
        }
    }

    suspend fun search(query: String): List<Ticker> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 2) {
            return emptyList()
        }

        val cacheKey = normalizedQuery.lowercase()
        val now = System.currentTimeMillis()
        searchMutex.withLock {
            val cached = searchCache[cacheKey]
            if (cached != null && cached.expiresAt > now) {
                return cached.results
            }
        }

        val results = withContext(Dispatchers.IO) {
            searchProvider.search(normalizedQuery)
        }

        searchMutex.withLock {
            searchCache[cacheKey] = SearchCacheEntry(
                expiresAt = System.currentTimeMillis() + 30_000L,
                results = results
            )
        }
        return results
    }

    /**
     * 관심종목에 Ticker 를 등록하는 메서드 입니다.
     *
     * @param ticker Ticker Object
     */
    fun addTicker(ticker: Ticker) {
        watchlistRepository.addTicker(ticker)
        ApplicationManager.getApplication().messageBus.syncPublisher(WatchlistEntryUpdateListener.TOPIC)
            .onWatchlistEntryUpdated()
        forceRefresh()
    }

    /**
     * 관심종목에 등록된 Ticker 를 삭제하는 메서드 입니다.
     *
     * @param symbol Ticker symbol
     */
    fun removeTicker(symbol: String, marketType: MarketType? = null) {
        watchlistRepository.removeTicker(symbol, marketType?.name)
        ApplicationManager.getApplication().messageBus.syncPublisher(WatchlistEntryUpdateListener.TOPIC)
            .onWatchlistEntryUpdated()
        forceRefresh()
    }

    /**
     * WatchlistEntry 의 포트폴리오 정보를 업데이트하는 메서드 입니다.
     *
     * @param entry WatchlistEntry Object (purchasePrice, quantity 포함)
     */
    fun updateWatchlistEntryPortfolio(entry: WatchlistRepository.WatchlistEntry) {
        watchlistRepository.updateWatchlistEntryPortfolio(entry)
        ApplicationManager.getApplication().messageBus.syncPublisher(WatchlistEntryUpdateListener.TOPIC)
            .onWatchlistEntryUpdated()
        forceRefresh()
    }

    /**
     * 현재 저장된 모든 WatchlistEntry 를 반환하는 메서드 입니다.
     */
    fun getWatchlistEntries(): List<WatchlistRepository.WatchlistEntry> {
        return watchlistRepository.getWatchlistEntries()
    }

    /**
     * 특정 Ticker 에 해당하는 WatchlistEntry 를 반환하는 메서드 입니다.
     */
    fun getWatchlistEntry(symbol: String, marketType: MarketType): WatchlistRepository.WatchlistEntry? {
        return watchlistRepository.getWatchlistEntries().find {
            it.symbol == symbol && MarketType.valueOf(it.marketType) == marketType
        }
    }

    /**
     * 강제로 가격을 갱신하는 메서드 입니다.
     */
    fun forceRefresh() {
        cs.launch(Dispatchers.IO) {
            refreshPrices()
        }
    }

}
