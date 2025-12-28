package com.github.myeoungdev.marketticker.application.repository

import com.github.myeoungdev.marketticker.domain.model.MarketType
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

    data class SavedTicker(
        var symbol: String = "",
        val tradingSymbol: String = "",
        var name: String = "",
        var marketType: String = "UNKNOWN",
        var nationCode: String? = "",
        var nationName: String? = ""
    )

    data class State(
        var tickers: MutableList<SavedTicker> = mutableListOf(),
        var updateIntervalSec: Long = 60L
    )

    private var marketTickerState = State()

    override fun getState(): State? = null

    override fun loadState(state: State) {
//        marketTickerState = state

        // NOTE: 개발용 State 초기화
        noStateLoaded()
    }

    fun getTickers(): List<Ticker> {
        return marketTickerState.tickers.map {
            logger.info { "it ${it}" }
            Ticker(
                it.symbol,
                it.tradingSymbol,
                it.name,
                MarketType.valueOf(it.marketType),
                it.nationCode,
                it.nationName
            )
        }
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
            SavedTicker(
                ticker.symbol,
                ticker.tradingSymbol,
                ticker.name,
                ticker.marketType.name,
                ticker.nationCode,
                ticker.nationName
            )
        )
    }

    fun removeTicker(symbol: String) {
        marketTickerState.tickers.removeIf { it.symbol == symbol }
    }

}