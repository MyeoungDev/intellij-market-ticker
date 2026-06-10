package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class MarketHoursPolicyTest {

    @Test
    fun `KRX 국내 종목은 평일 정규장 시간에만 polling 대상이다`() {
        val ticker = ticker(MarketType.KOSPI)

        assertThat(
            MarketHoursPolicy.isPollable(
                ticker,
                seoulTime("2026-06-10T09:00:00+09:00"),
                AppSettingsService.DomesticTradeVenueMode.KRX_ONLY
            )
        ).isTrue()
        assertThat(
            MarketHoursPolicy.isPollable(
                ticker,
                seoulTime("2026-06-10T15:30:00+09:00"),
                AppSettingsService.DomesticTradeVenueMode.KRX_ONLY
            )
        ).isFalse()
        assertThat(
            MarketHoursPolicy.isPollable(
                ticker,
                seoulTime("2026-06-13T10:00:00+09:00"),
                AppSettingsService.DomesticTradeVenueMode.KRX_ONLY
            )
        ).isFalse()
    }

    @Test
    fun `NXT와 MIXED 국내 종목은 평일 NXT 가능 시간에 polling 대상이다`() {
        val ticker = ticker(MarketType.KOSDAQ)

        assertThat(
            MarketHoursPolicy.isPollable(
                ticker,
                seoulTime("2026-06-10T08:00:00+09:00"),
                AppSettingsService.DomesticTradeVenueMode.NXT_ONLY
            )
        ).isTrue()
        assertThat(
            MarketHoursPolicy.isPollable(
                ticker,
                seoulTime("2026-06-10T19:59:00+09:00"),
                AppSettingsService.DomesticTradeVenueMode.MIXED
            )
        ).isTrue()
        assertThat(
            MarketHoursPolicy.isPollable(
                ticker,
                seoulTime("2026-06-10T20:00:00+09:00"),
                AppSettingsService.DomesticTradeVenueMode.MIXED
            )
        ).isFalse()
    }

    @Test
    fun `미국 종목은 평일 정규장 시간에만 polling 대상이다`() {
        val ticker = ticker(MarketType.NASDAQ)

        assertThat(
            MarketHoursPolicy.isPollable(
                ticker,
                newYorkTime("2026-06-10T09:30:00-04:00"),
                AppSettingsService.DomesticTradeVenueMode.MIXED
            )
        ).isTrue()
        assertThat(
            MarketHoursPolicy.isPollable(
                ticker,
                newYorkTime("2026-06-10T16:00:00-04:00"),
                AppSettingsService.DomesticTradeVenueMode.MIXED
            )
        ).isFalse()
        assertThat(
            MarketHoursPolicy.isPollable(
                ticker,
                newYorkTime("2026-06-13T10:00:00-04:00"),
                AppSettingsService.DomesticTradeVenueMode.MIXED
            )
        ).isFalse()
    }

    @Test
    fun `코인과 아직 시간표를 지원하지 않는 해외 시장은 항상 polling 대상이다`() {
        val now = seoulTime("2026-06-13T03:00:00+09:00")

        assertThat(MarketHoursPolicy.isPollable(ticker(MarketType.UPBIT), now, AppSettingsService.DomesticTradeVenueMode.MIXED)).isTrue()
        assertThat(MarketHoursPolicy.isPollable(ticker(MarketType.TOKYO), now, AppSettingsService.DomesticTradeVenueMode.MIXED)).isTrue()
        assertThat(MarketHoursPolicy.isPollable(ticker(MarketType.SHANGHAI), now, AppSettingsService.DomesticTradeVenueMode.MIXED)).isTrue()
        assertThat(MarketHoursPolicy.isPollable(ticker(MarketType.HONG_KONG), now, AppSettingsService.DomesticTradeVenueMode.MIXED)).isTrue()
        assertThat(MarketHoursPolicy.isPollable(ticker(MarketType.VIETNAM), now, AppSettingsService.DomesticTradeVenueMode.MIXED)).isTrue()
    }

    @Test
    fun `혼합 watchlist 에서 polling 가능한 종목만 반환한다`() {
        val tickers = listOf(
            ticker(MarketType.KOSPI, "005930"),
            ticker(MarketType.NASDAQ, "NVDA"),
            ticker(MarketType.UPBIT, "BTC")
        )

        val result = MarketHoursPolicy.filterPollable(
            tickers,
            seoulTime("2026-06-10T23:00:00+09:00"),
            AppSettingsService.DomesticTradeVenueMode.MIXED
        )

        assertThat(result.map { it.symbol }).containsExactly("NVDA", "BTC")
    }

    private fun ticker(marketType: MarketType, symbol: String = marketType.name): Ticker {
        return Ticker(
            symbol = symbol,
            tradingSymbol = symbol,
            name = symbol,
            marketType = marketType,
            nationCode = marketType.country.name,
            nationName = marketType.country.name
        )
    }

    private fun seoulTime(value: String): ZonedDateTime = ZonedDateTime.parse(value)

    private fun newYorkTime(value: String): ZonedDateTime = ZonedDateTime.parse(value)
}
