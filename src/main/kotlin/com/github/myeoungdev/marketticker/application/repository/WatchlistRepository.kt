package com.github.myeoungdev.marketticker.application.repository

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.MarketType
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

    /**
     * 저장된 상태를 로드하면서 구버전/누락 필드를 현재 스키마에 맞게 보정합니다.
     */
    override fun loadState(state: State) {
        marketTickerState = State(
            tickers = state.tickers
                .map { normalizeEntry(it) }
                .toMutableList(),
            updateIntervalSec = state.updateIntervalSec
        )
    }

    /**
     * 외부로는 내부 상태의 복사본만 반환합니다.
     */
    fun getWatchlistEntries(): List<WatchlistEntry> {
        return marketTickerState.tickers.map { it.copy() }
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
            normalizeEntry(
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

    fun clearPortfolio(symbol: String, marketType: String) {
        val index = marketTickerState.tickers.indexOfFirst {
            it.symbol == symbol && it.marketType == marketType
        }

        if (index != -1) {
            val watchlistEntry = marketTickerState.tickers[index]
            watchlistEntry.purchasePrice = null
            watchlistEntry.quantity = null
            watchlistEntry.targetWeightPercentage = null
            watchlistEntry.realizedProfitLoss = 0.0
            logger.info { "Cleared portfolio for ${watchlistEntry.symbol}" }
        } else {
            logger.warn { "Watchlist entry not found for portfolio clear: $symbol/$marketType" }
        }
    }

    private fun normalizeEntry(entry: WatchlistEntry): WatchlistEntry {
        val normalizedMarketType = MarketType.of(entry.marketType).name
        val normalizedTradingSymbol = entry.tradingSymbol.ifBlank { entry.symbol }
        val normalizedGroupTag = entry.groupTag.ifBlank { defaultGroupTagFor(normalizedMarketType) }

        return entry.copy(
            tradingSymbol = normalizedTradingSymbol,
            marketType = normalizedMarketType,
            realizedProfitLoss = entry.realizedProfitLoss ?: 0.0,
            groupTag = normalizedGroupTag
        )
    }

    private fun defaultGroupTagFor(marketType: String): String {
        return when {
            MarketType.of(marketType).isKoreanMarket() -> "국내"
            MarketType.of(marketType).isGlobalStockMarket() -> "해외"
            else -> "기타"
        }
    }

}
