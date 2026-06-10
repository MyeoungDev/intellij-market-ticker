package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.fixtures.domain.TickerPriceFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class PriceRefreshPlannerTest {

    @Test
    fun `자동 refresh는 장외 종목을 요청 대상에서 제외한다`() {
        val tickers = listOf(
            ticker(MarketType.KOSPI, "005930"),
            ticker(MarketType.NASDAQ, "NVDA"),
            ticker(MarketType.UPBIT, "BTC")
        )

        val result = PriceRefreshPlanner.selectTickers(
            tickers = tickers,
            source = PriceRefreshSource.AUTOMATIC,
            now = ZonedDateTime.parse("2026-06-10T23:00:00+09:00"),
            domesticVenueMode = AppSettingsService.DomesticTradeVenueMode.MIXED
        )

        assertThat(result.map { it.symbol }).containsExactly("NVDA", "BTC")
    }

    @Test
    fun `자동 refresh에서 요청 가능한 종목이 없으면 빈 요청 대상을 반환한다`() {
        val tickers = listOf(ticker(MarketType.KOSPI, "005930"))

        val result = PriceRefreshPlanner.selectTickers(
            tickers = tickers,
            source = PriceRefreshSource.AUTOMATIC,
            now = ZonedDateTime.parse("2026-06-10T21:00:00+09:00"),
            domesticVenueMode = AppSettingsService.DomesticTradeVenueMode.MIXED
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `수동 refresh는 market-hours 필터를 우회한다`() {
        val tickers = listOf(ticker(MarketType.KOSPI, "005930"))

        val result = PriceRefreshPlanner.selectTickers(
            tickers = tickers,
            source = PriceRefreshSource.MANUAL,
            now = ZonedDateTime.parse("2026-06-10T21:00:00+09:00"),
            domesticVenueMode = AppSettingsService.DomesticTradeVenueMode.MIXED
        )

        assertThat(result).containsExactlyElementsOf(tickers)
    }

    @Test
    fun `시작 refresh는 초기 가격 표시를 위해 market-hours 필터를 우회한다`() {
        val tickers = listOf(ticker(MarketType.NASDAQ, "MU"))

        val result = PriceRefreshPlanner.selectTickers(
            tickers = tickers,
            source = PriceRefreshSource.STARTUP,
            now = ZonedDateTime.parse("2026-06-10T13:51:00+09:00"),
            domesticVenueMode = AppSettingsService.DomesticTradeVenueMode.MIXED
        )

        assertThat(result).containsExactlyElementsOf(tickers)
    }

    @Test
    fun `부분 자동 refresh 응답은 기존 가격과 병합한다`() {
        val current = listOf(
            TickerPriceFixtures.SAMSUNG_KRW.copy(currentPrice = 72_000.0),
            TickerPriceFixtures.create(symbol = "NVDA", tradingSymbol = "NVDA.O", name = "NVIDIA", marketType = MarketType.NASDAQ, currentPrice = 490.0)
        )
        val fetched = listOf(
            TickerPriceFixtures.create(symbol = "NVDA", tradingSymbol = "NVDA.O", name = "NVIDIA", marketType = MarketType.NASDAQ, currentPrice = 500.0),
            TickerPriceFixtures.create(symbol = "BTC", tradingSymbol = "BTC_KRW_UPBIT", name = "비트코인", marketType = MarketType.UPBIT, currentPrice = 100_000_000.0)
        )

        val result = PriceRefreshPlanner.mergePrices(current, fetched)

        assertThat(result.map { it.symbol }).containsExactly("005930", "NVDA", "BTC")
        assertThat(result.first { it.symbol == "005930" }.currentPrice).isEqualTo(72_000.0)
        assertThat(result.first { it.symbol == "NVDA" }.currentPrice).isEqualTo(500.0)
    }

    private fun ticker(marketType: MarketType, symbol: String): Ticker {
        return Ticker(
            symbol = symbol,
            tradingSymbol = symbol,
            name = symbol,
            marketType = marketType,
            nationCode = marketType.country.name,
            nationName = marketType.country.name
        )
    }
}
