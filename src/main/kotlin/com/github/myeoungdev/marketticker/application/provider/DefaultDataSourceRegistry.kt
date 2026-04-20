package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.infrastructure.naver.NaverClient
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverChartProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverMarketIndicatorProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverNewsProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverPriceProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverResearchProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverScreenerProvider
import com.github.myeoungdev.marketticker.infrastructure.finviz.FinvizCalendarProvider
import com.github.myeoungdev.marketticker.infrastructure.finviz.FinvizClient

/**
 * 현재 기본 데이터 소스 구현체를 조립하는 레지스트리입니다.
 */
object DefaultDataSourceRegistry {

    private val naverClient: NaverClient by lazy { NaverClient() }
    private val finvizClient: FinvizClient by lazy { FinvizClient() }
    private val priceProvider: PriceProvider by lazy { NaverPriceProvider(naverClient) }
    private val searchProvider: SearchProvider by lazy { NaverSearchProvider(naverClient) }
    private val newsProvider: NewsProvider by lazy { NaverNewsProvider(naverClient) }
    private val researchProvider: ResearchProvider by lazy { NaverResearchProvider(naverClient) }
    private val screenerProvider: ScreenerProvider by lazy { NaverScreenerProvider(naverClient) }
    private val chartProvider: ChartProvider by lazy { NaverChartProvider(naverClient) }
    private val marketIndicatorProvider: MarketIndicatorProvider by lazy { NaverMarketIndicatorProvider(naverClient) }
    private val calendarProvider: CalendarProvider by lazy { FinvizCalendarProvider(finvizClient) }

    fun priceProvider(): PriceProvider = priceProvider

    fun searchProvider(): SearchProvider = searchProvider

    fun newsProvider(): NewsProvider = newsProvider

    fun researchProvider(): ResearchProvider = researchProvider

    fun screenerProvider(): ScreenerProvider = screenerProvider

    fun chartProvider(): ChartProvider = chartProvider

    fun marketIndicatorProvider(): MarketIndicatorProvider = marketIndicatorProvider

    fun calendarProvider(): CalendarProvider = calendarProvider
}
