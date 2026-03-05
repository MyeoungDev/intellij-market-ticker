package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.fixtures.domain.TickerPriceFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PriceHistoryServiceTest {

    @Test
    fun `append 후 DAY 캔들을 생성할 수 있다`() {
        val service = PriceHistoryService()
        val price = TickerPriceFixtures.APPLE_RISING

        repeat(5) {
            service.append(listOf(price.copy(currentPrice = 180.0 + it)))
        }

        val candles = service.buildCandles(
            symbol = price.symbol,
            marketType = price.marketType.name,
            period = PriceHistoryService.Period.DAY,
            zoneId = MarketType.NASDAQ.zoneId
        )

        assertThat(candles).isNotEmpty
        assertThat(candles.last().high).isGreaterThanOrEqualTo(candles.last().low)
    }

    @Test
    fun `movingAverage는 window 이전 구간을 null로 채운다`() {
        val service = PriceHistoryService()
        val candles = listOf(
            PriceHistoryService.Candle(java.time.Instant.now(), 1.0, 1.0, 1.0, 1.0, 10),
            PriceHistoryService.Candle(java.time.Instant.now(), 2.0, 2.0, 2.0, 2.0, 10),
            PriceHistoryService.Candle(java.time.Instant.now(), 3.0, 3.0, 3.0, 3.0, 10)
        )

        val ma = service.movingAverage(candles, 2)

        assertThat(ma).containsExactly(null, 1.5, 2.5)
    }
}
