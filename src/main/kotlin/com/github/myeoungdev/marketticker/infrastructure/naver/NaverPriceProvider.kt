package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.PriceProvider
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice

/**
 * Naver 시세 응답을 플러그인 가격 모델로 변환하는 provider 구현체입니다.
 */
class NaverPriceProvider(
    private val client: NaverClient = NaverClient()
) : PriceProvider {

    override fun getPrices(tickers: List<Ticker>): List<TickerPrice> {
        val stockPrices = client.fetchStockPrice(tickers).map { it.toTickerPrice() }
        val coinPrices = client.fetchCoinPrice(tickers).map { it.toTickerPrice() }
        return stockPrices + coinPrices
    }
}
