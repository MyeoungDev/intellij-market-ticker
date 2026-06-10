package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

object MarketHoursPolicy {
    private val KRX_OPEN: LocalTime = LocalTime.of(9, 0)
    private val KRX_CLOSE: LocalTime = LocalTime.of(15, 30)
    private val NXT_OPEN: LocalTime = LocalTime.of(8, 0)
    private val NXT_CLOSE: LocalTime = LocalTime.of(20, 0)
    private val US_OPEN: LocalTime = LocalTime.of(9, 30)
    private val US_CLOSE: LocalTime = LocalTime.of(16, 0)

    fun filterPollable(
        tickers: List<Ticker>,
        now: ZonedDateTime = ZonedDateTime.now(),
        domesticVenueMode: AppSettingsService.DomesticTradeVenueMode
    ): List<Ticker> {
        return tickers.filter { ticker -> isPollable(ticker, now, domesticVenueMode) }
    }

    fun isPollable(
        ticker: Ticker,
        now: ZonedDateTime = ZonedDateTime.now(),
        domesticVenueMode: AppSettingsService.DomesticTradeVenueMode
    ): Boolean {
        val marketNow = now.withZoneSameInstant(ticker.marketType.zoneId)
        return when {
            ticker.marketType.isCryptoMarket() -> true
            ticker.marketType.isKoreanMarket() -> isDomesticPollable(marketNow, domesticVenueMode)
            ticker.marketType in setOf(MarketType.USA, MarketType.NASDAQ, MarketType.NYSE) -> {
                isWeekday(marketNow) && isInRange(marketNow.toLocalTime(), US_OPEN, US_CLOSE)
            }
            else -> true
        }
    }

    private fun isDomesticPollable(
        now: ZonedDateTime,
        domesticVenueMode: AppSettingsService.DomesticTradeVenueMode
    ): Boolean {
        if (!isWeekday(now)) return false

        val (open, close) = when (domesticVenueMode) {
            AppSettingsService.DomesticTradeVenueMode.KRX_ONLY -> KRX_OPEN to KRX_CLOSE
            AppSettingsService.DomesticTradeVenueMode.NXT_ONLY,
            AppSettingsService.DomesticTradeVenueMode.MIXED -> NXT_OPEN to NXT_CLOSE
        }
        return isInRange(now.toLocalTime(), open, close)
    }

    private fun isWeekday(now: ZonedDateTime): Boolean {
        return now.dayOfWeek != DayOfWeek.SATURDAY && now.dayOfWeek != DayOfWeek.SUNDAY
    }

    private fun isInRange(now: LocalTime, open: LocalTime, close: LocalTime): Boolean {
        return !now.isBefore(open) && now.isBefore(close)
    }
}
