package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-09
 */
interface PriceProvider {
    fun fetchPrices(tickers: List<Ticker>): List<TickerPrice>
}