package com.github.myeoungdev.marketticker.application.manager

import com.github.myeoungdev.marketticker.application.provider.PriceProvider
import com.github.myeoungdev.marketticker.application.provider.SearchProvider
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverPriceProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchProvider
import com.intellij.openapi.components.Service
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Some description here.
 *
 * @author : 강명관
 * @since : 1.0
 **/

private val logger = KotlinLogging.logger {}

@Service(Service.Level.APP)
class MarketTickerManager(private val cs: CoroutineScope) {

    private val priceProvider: PriceProvider = NaverPriceProvider()
    private val searchProvider: SearchProvider = NaverSearchProvider()
    private val watchlistRepository = WatchlistRepository.getInstance()

    private val _currentPrices = MutableStateFlow<List<TickerPrice>>(emptyList())
    val currentPrices: StateFlow<List<TickerPrice>> = _currentPrices.asStateFlow()

    init {
        logger.info { "Service Initialized. Starting polling..." }
        startPolling()
    }

    private fun startPolling() {
        cs.launch {
            while (isActive) {
                refreshPrices()
                delay(60_000)
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

    fun removeTickerFromWatchlist(symbol: String) {
        watchlistRepository.removeTicker(symbol)

        cs.launch {
            refreshPrices()
        }
    }

    suspend fun refreshPrices() {
        val savedTickers = watchlistRepository.getTickers()
        logger.info { "Saved tickers count: ${savedTickers.size}" }
        logger.info { "Saved tickers : ${savedTickers.toString()}" }

        if (savedTickers.isNotEmpty()) {
            val prices = priceProvider.fetchPrices(savedTickers)
            _currentPrices.emit(prices)
        }

        val prices = priceProvider.fetchPrices(savedTickers)
        logger.info { "Fetched prices count: ${prices.size}" }

        _currentPrices.emit(prices)
    }
}