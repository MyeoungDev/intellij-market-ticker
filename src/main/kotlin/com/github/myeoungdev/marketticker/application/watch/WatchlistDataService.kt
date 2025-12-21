package com.github.myeoungdev.marketticker.application.watch

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.XCollection
import fleet.util.logging.logger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
private val logger = KotlinLogging.logger {}

@State(
    name = "MarketTickerWatchlist",
    storages = [Storage("market_ticker_watchlist.xml")]
)
@Service(Service.Level.APP)
class WatchlistDataService : PersistentStateComponent<WatchlistDataService.State> {

    data class State(
        @get:XCollection(style = XCollection.Style.v2) // 컬렉션 직렬화 방식 지정
        var tickers: MutableList<Ticker> = ArrayList()
    )

    private var watchlistState = State()

    override fun getState(): State {
        return watchlistState
    }

    override fun loadState(state: State) {
        watchlistState = state
    }

    fun getWatchlist(): List<Ticker> {
        return watchlistState.tickers.toList()
    }

    fun addTicker(ticker: Ticker) {
        // 중복 체크
        if (watchlistState.tickers.none { it.symbol == ticker.symbol }) {
            logger.info { "save ticker ${ticker}" }
            watchlistState.tickers.add(ticker)
        }
    }

    fun removeTicker(symbol: String) {
        watchlistState.tickers.removeIf { it.symbol == symbol }
    }
}