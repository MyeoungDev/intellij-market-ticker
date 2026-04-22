package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.ChartProvider
import com.github.myeoungdev.marketticker.application.service.PriceHistoryService
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker

class NaverChartProvider(
    private val client: NaverClient = NaverClient()
) : ChartProvider {

    override fun getStockCandles(
        ticker: Ticker,
        period: PriceHistoryService.Period
    ): List<PriceHistoryService.Candle> {
        return client.fetchStockChartCandles(ticker, period)
            .map { it.toPriceHistoryCandle(ticker.marketType.zoneId) }
    }

    override fun getCryptoCandles(
        ticker: Ticker,
        marketType: MarketType,
        period: PriceHistoryService.Period
    ): List<PriceHistoryService.Candle> {
        val now = java.time.LocalDateTime.now(marketType.zoneId)
        val from = when (period) {
            PriceHistoryService.Period.DAY -> now.minusDays(30)
            PriceHistoryService.Period.WEEK -> now.minusDays(90)
            PriceHistoryService.Period.MONTH -> now.minusDays(180)
            PriceHistoryService.Period.YEAR -> now.minusDays(365 * 3L)
        }

        val chartCandles = client.fetchCryptoChartCandles(
            exchangeType = marketType.name,
            nfTicker = ticker.symbol,
            marketType = "KRW",
            from = from,
            to = now
        ).map { it.toPriceHistoryCandle() }

        return client.fetchCoinOverview(marketType.name, ticker.symbol)
            ?.mergeDailyCandles(chartCandles, marketType.zoneId)
            ?: chartCandles
    }
}
