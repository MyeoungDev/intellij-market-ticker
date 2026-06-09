package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.DomesticAlternativePrice
import com.github.myeoungdev.marketticker.domain.model.DomesticTradeType
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.PriceStatus
import com.github.myeoungdev.marketticker.fixtures.domain.TickerPriceFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DomesticDisplayPriceSelectorTest {

    @Test
    fun `NXT 고정 모드는 국내 종목 표시가를 대체 거래소 가격으로 바꾼다`() {
        val settings = AppSettingsService()
        settings.setDomesticTradeVenueMode(AppSettingsService.DomesticTradeVenueMode.NXT_ONLY)
        val selector = DomesticDisplayPriceSelector(settings)
        val krxPrice = TickerPriceFixtures.SAMSUNG_KRW.copy(
            currentPrice = 72000.0,
            changeAmount = 800.0,
            changeRate = 1.2,
            overMarketPrice = DomesticAlternativePrice(
                currentPrice = 72500.0,
                changeAmount = 1300.0,
                changeRate = 1.83,
                tradeVolume = 1_200_000L,
                marketStatus = MarketStatus.OPEN
            )
        )

        val selected = selector.select(krxPrice)

        assertThat(selected.currentPrice).isEqualTo(72500.0)
        assertThat(selected.changeAmount).isEqualTo(1300.0)
        assertThat(selected.changeRate).isEqualTo(1.83)
        assertThat(selected.tradeVolume).isEqualTo(1_200_000L)
        assertThat(selected.priceStatus).isEqualTo(PriceStatus.RISING)
    }

    @Test
    fun `KRX 고정 모드는 국내 종목 원본 가격을 유지한다`() {
        val settings = AppSettingsService()
        settings.setDomesticTradeVenueMode(AppSettingsService.DomesticTradeVenueMode.KRX_ONLY)
        val selector = DomesticDisplayPriceSelector(settings)
        val krxPrice = TickerPriceFixtures.SAMSUNG_KRW.copy(
            currentPrice = 72000.0,
            overMarketPrice = DomesticAlternativePrice(currentPrice = 72500.0)
        )

        val selected = selector.select(krxPrice)

        assertThat(selected.currentPrice).isEqualTo(72000.0)
    }

    @Test
    fun `혼합 모드는 NXT 장 상태가 열려 있지 않으면 원본 KRX 가격을 유지한다`() {
        val settings = AppSettingsService()
        settings.setDomesticTradeVenueMode(AppSettingsService.DomesticTradeVenueMode.MIXED)
        val selector = DomesticDisplayPriceSelector(settings) { DomesticTradeType.NXT }
        val krxPrice = TickerPriceFixtures.SAMSUNG_KRW.copy(
            currentPrice = 72000.0,
            overMarketPrice = DomesticAlternativePrice(
                currentPrice = 72500.0,
                marketStatus = MarketStatus.CLOSED
            )
        )

        val selected = selector.select(krxPrice)

        assertThat(selected.currentPrice).isEqualTo(72000.0)
    }

    @Test
    fun `해외 종목은 NXT 고정 모드에서도 원본 가격을 유지한다`() {
        val settings = AppSettingsService()
        settings.setDomesticTradeVenueMode(AppSettingsService.DomesticTradeVenueMode.NXT_ONLY)
        val selector = DomesticDisplayPriceSelector(settings)
        val usPrice = TickerPriceFixtures.create(
            symbol = "NVDA",
            tradingSymbol = "NVDA.O",
            name = "NVIDIA",
            marketType = MarketType.NASDAQ,
            currency = CurrencyType.USD,
            currentPrice = 490.0,
            changeAmount = 5.0
        )

        val selected = selector.select(usPrice)

        assertThat(selected).isEqualTo(usPrice)
    }
}
