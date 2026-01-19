package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.listener.TickerUpdateListener
import com.github.myeoungdev.marketticker.application.provider.PriceProvider
import com.github.myeoungdev.marketticker.application.provider.SearchProvider
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverPriceProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchProvider
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

    private val watchlistRepository = service<WatchlistRepository>()
    private val notificationService = service<NotificationService>()

    private val priceProvider: PriceProvider = NaverPriceProvider()
    private val searchProvider: SearchProvider = NaverSearchProvider()

    private val _currentPrices = MutableStateFlow<List<TickerPrice>>(emptyList())
    val currentPrices: StateFlow<List<TickerPrice>> = _currentPrices.asStateFlow()

    /**
     * 등록된 Ticker 의 실시간 가격을 갱신하는 메서드 입니다.
     */
    suspend fun refreshPrices() {
        val savedTickers = watchlistRepository.getTickers()

        if (savedTickers.isEmpty()) return

        try {
            val prices = priceProvider.getPrices(savedTickers)
            logger.info { "Fetched prices count: ${prices.size}" }

            _currentPrices.emit(prices)

            notificationService.checkAndNotify(prices)

            ProjectManager.getInstance().openProjects.forEach { project ->
                if (!project.isDisposed) {
                    project.messageBus.syncPublisher(TickerUpdateListener.TOPIC).onTickerUpdated(prices)
                }
            }

            broadcastUpdate(prices)

        } catch (e: Exception) {
            logger.error(e) { "Failed to refresh prices" }
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
        if (query.length < 2) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            searchProvider.search(query)
        }
    }

    /**
     * 관심종목에 Ticker 를 등록하는 메서드 입니다.
     *
     * @param ticker Ticker Object
     */
    fun addTicker(ticker: Ticker) {
        watchlistRepository.addTicker(ticker)
        forceRefresh()
    }

    /**
     * 관심종목에 등록된 Ticker 를 삭제하는 메서드 입니다.
     *
     * @param symbol Ticker symbol
     */
    fun removeTicker(symbol: String) {
        watchlistRepository.removeTicker(symbol)
        forceRefresh()
    }

    /**
     * 강제로 가격을 갱신하는 메서드 입니다.
     */
    fun forceRefresh() {
        cs.launch {
            refreshPrices()
        }
    }


}