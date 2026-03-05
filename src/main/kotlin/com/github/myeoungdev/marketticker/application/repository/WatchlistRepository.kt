package com.github.myeoungdev.marketticker.application.repository

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

@State(
    name = "WatchlistRepository",
    storages = [Storage("market_ticker_watchlist.xml")]
)
@Service(Service.Level.APP)
class WatchlistRepository : PersistentStateComponent<WatchlistRepository.State> {

    data class WatchlistEntry(
        var symbol: String = "",
        var tradingSymbol: String = "",
        var name: String = "",
        var marketType: String = "UNKNOWN",
        var nationCode: String? = "",
        var nationName: String? = "",
        var purchasePrice: Double? = null,
        var quantity: Double? = null,
        var targetWeightPercentage: Double? = null,
        var realizedProfitLoss: Double? = 0.0,
        var groupTag: String = ""
    )

    data class State(
        var tickers: MutableList<WatchlistEntry> = mutableListOf(), // Changed type here
        var updateIntervalSec: Long = 60L
    )

    private var marketTickerState = State()

    override fun getState(): State = marketTickerState

    override fun loadState(state: State) {
        marketTickerState = state
    }

    fun getWatchlistEntries(): List<WatchlistEntry> {
        return marketTickerState.tickers.map { it }
    }

    fun addTicker(ticker: Ticker) {
        val exists = marketTickerState.tickers.any {
            it.symbol == ticker.symbol && it.marketType == ticker.marketType.name
        }

        if (exists) {
            logger.info { "Ticker ${ticker.name} (${ticker.symbol}) is already in watchlist." }
            return
        }

        marketTickerState.tickers.add(
            WatchlistEntry(
                ticker.symbol,
                ticker.tradingSymbol,
                ticker.name,
                ticker.marketType.name,
                ticker.nationCode,
                ticker.nationName,
                null,
                null,
                null,
                0.0,
                defaultGroupTagFor(ticker.marketType.name)
            )
        )
    }

    fun removeTicker(symbol: String, marketType: String? = null) {
        marketTickerState.tickers.removeIf {
            it.symbol == symbol && (marketType == null || it.marketType == marketType)
        }
    }

    fun updateWatchlistEntryPortfolio(updatedEntry: WatchlistEntry) {
        val index = marketTickerState.tickers.indexOfFirst {
            it.symbol == updatedEntry.symbol && it.marketType == updatedEntry.marketType
        }

        if (index != -1) {
            val watchlistEntry = marketTickerState.tickers[index]
            watchlistEntry.purchasePrice = updatedEntry.purchasePrice
            watchlistEntry.quantity = updatedEntry.quantity
            watchlistEntry.targetWeightPercentage = updatedEntry.targetWeightPercentage
            watchlistEntry.realizedProfitLoss = updatedEntry.realizedProfitLoss ?: 0.0
            watchlistEntry.groupTag = updatedEntry.groupTag.ifBlank {
                defaultGroupTagFor(updatedEntry.marketType)
            }
            logger.info { "Updated portfolio for ${watchlistEntry.symbol}: purchasePrice=${watchlistEntry.purchasePrice}, quantity=${watchlistEntry.quantity}" } // Corrected logging variable
        } else {
            logger.warn { "Watchlist entry ${updatedEntry.name} not found in watchlist for portfolio update." }
        }
    }

    private fun defaultGroupTagFor(marketType: String): String {
        return when (marketType) {
            "KOSPI", "KOSDAQ" -> "국내"
            "NASDAQ", "NYSE" -> "해외"
            else -> "기타"
        }
    }

}
