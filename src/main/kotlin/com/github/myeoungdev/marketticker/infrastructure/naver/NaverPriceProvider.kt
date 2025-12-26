package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.PriceProvider
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-09
 */
class NaverPriceProvider(
    private val client: NaverClient = NaverClient()
) : PriceProvider {

    override fun getPrices(tickers: List<Ticker>): List<TickerPrice> {
        return client.fetchStockPrice(tickers).map { it.toTickerPrice() }
    }
}