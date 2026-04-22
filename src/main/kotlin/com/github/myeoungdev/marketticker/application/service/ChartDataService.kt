package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.provider.ChartProvider
import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.components.Service

/**
 * 차트 뷰에서 사용할 캔들 로딩/폴백 규칙을 조합하는 애플리케이션 서비스입니다.
 */
@Service(Service.Level.APP)
class ChartDataService(
    private val historyService: PriceHistoryService = com.intellij.openapi.components.service(),
    private val chartProvider: ChartProvider = DefaultDataSourceRegistry.chartProvider()
) {

    fun loadCandles(
        ticker: Ticker,
        period: PriceHistoryService.Period
    ): List<PriceHistoryService.Candle> {
        val marketType = ticker.marketType

        return if (marketType.isCryptoMarket()) {
            val remote = chartProvider.getCryptoCandles(ticker, marketType, period)
            if (period != PriceHistoryService.Period.DAY || remote.isNotEmpty()) {
                remote
            } else {
                historyService.buildCandles(ticker.symbol, marketType.name, period, marketType.zoneId)
            }
        } else {
            val remote = chartProvider.getStockCandles(ticker, period)
            if (remote.isNotEmpty()) {
                remote
            } else {
                historyService.buildCandles(ticker.symbol, marketType.name, period, marketType.zoneId)
            }
        }
    }
}
