package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import java.time.ZonedDateTime

object PriceRefreshPlanner {

    fun selectTickers(
        tickers: List<Ticker>,
        source: PriceRefreshSource,
        now: ZonedDateTime = ZonedDateTime.now(),
        domesticVenueMode: AppSettingsService.DomesticTradeVenueMode
    ): List<Ticker> {
        return when (source) {
            PriceRefreshSource.AUTOMATIC -> MarketHoursPolicy.filterPollable(tickers, now, domesticVenueMode)
            PriceRefreshSource.MANUAL,
            PriceRefreshSource.STARTUP -> tickers
        }
    }

    fun mergePrices(
        currentPrices: List<TickerPrice>,
        fetchedPrices: List<TickerPrice>
    ): List<TickerPrice> {
        if (currentPrices.isEmpty()) return fetchedPrices

        val fetchedByKey = fetchedPrices.associateBy { it.symbol to it.marketType }
        val currentKeys = currentPrices.map { it.symbol to it.marketType }.toSet()
        val updatedCurrent = currentPrices.map { price ->
            fetchedByKey[price.symbol to price.marketType] ?: price
        }
        val newPrices = fetchedPrices.filterNot { (it.symbol to it.marketType) in currentKeys }
        return updatedCurrent + newPrices
    }
}
