package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice

/**
 * 현재가 데이터를 공급하는 provider 인터페이스입니다.
 */
interface PriceProvider {
    fun getPrices(tickers: List<Ticker>): List<TickerPrice>
}
