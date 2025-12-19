package com.github.myeoungdev.marketticker.application.price

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverClient

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-09
 */
class NaverPriceProvider(
    private val client: NaverClient = NaverClient()
) : PriceProvider {

    override fun fetchPrices(tickers: List<Ticker>): List<TickerPrice> {
        return client.fetchStockPrice(tickers)
    }
}