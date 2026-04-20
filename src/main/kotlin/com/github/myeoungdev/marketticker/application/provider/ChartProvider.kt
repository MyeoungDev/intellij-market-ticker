package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.application.service.PriceHistoryService
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * 차트용 캔들 데이터를 공급하는 provider 인터페이스입니다.
 */
interface ChartProvider {

    fun getStockCandles(
        ticker: Ticker,
        period: PriceHistoryService.Period
    ): List<PriceHistoryService.Candle>

    fun getCryptoCandles(
        ticker: Ticker,
        marketType: MarketType,
        period: PriceHistoryService.Period
    ): List<PriceHistoryService.Candle>
}
