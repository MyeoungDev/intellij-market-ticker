package com.github.myeoungdev.marketticker.application.manager

import com.github.myeoungdev.marketticker.application.listener.TickerUpdateListener
import com.github.myeoungdev.marketticker.application.provider.PriceProvider
import com.github.myeoungdev.marketticker.application.provider.SearchProvider
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.application.service.NotificationService
import com.github.myeoungdev.marketticker.application.service.PriceAlertService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverPriceProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchProvider
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Some description here.
 *
 * @author : 강명관
 * @since : 1.0
 **/

private val logger = KotlinLogging.logger {}

@Service(Service.Level.APP)
class MarketTickerManager(
    private val cs: CoroutineScope,
) {

    private val watchlistRepository = service<WatchlistRepository>()
    private val notificationService = service<NotificationService>()

    private val priceProvider: PriceProvider = NaverPriceProvider()
    private val searchProvider: SearchProvider = NaverSearchProvider()

    private val _currentPrices = MutableStateFlow<List<TickerPrice>>(emptyList())
    val currentPrices: StateFlow<List<TickerPrice>> = _currentPrices.asStateFlow()

    companion object {
        const val POLLING_INTERVAL_MS = 6000L
    }

    init {
        logger.info { "Service Initialized. Starting polling..." }
        startPolling()
    }

    private fun startPolling() {
        cs.launch {
            while (isActive) {
                try {
                    refreshPrices()
                } catch (e: Exception) {
                    logger.error(e) { "Error during polling" }
                }
                delay(POLLING_INTERVAL_MS)
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

    fun addTickerToWatchlist(ticker: Ticker) {
        watchlistRepository.addTicker(ticker)

        cs.launch {
            refreshPrices()
        }
    }

    fun forceRefresh() {
        cs.launch {
            refreshPrices()
        }
    }

    fun removeTickerFromWatchlist(symbol: String) {
        watchlistRepository.removeTicker(symbol)

        cs.launch {
            refreshPrices()
        }
    }

    private suspend fun refreshPrices() {
        val savedTickers = watchlistRepository.getTickers()

        if (savedTickers.isEmpty()) return

        try {
            val prices = priceProvider.getPrices(savedTickers)
            logger.info { "Fetched prices count: ${prices.size}" }

            prices.forEach { logger.info { "tickerPrice : ${it}" } }

            _currentPrices.emit(prices)

            notificationService.checkAndNotify(prices)

            ProjectManager.getInstance().openProjects.forEach { project ->
                if (!project.isDisposed) {
                    project.messageBus.syncPublisher(TickerUpdateListener.TOPIC).onTickerUpdated(prices)
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to refresh prices" }
        }
    }

}