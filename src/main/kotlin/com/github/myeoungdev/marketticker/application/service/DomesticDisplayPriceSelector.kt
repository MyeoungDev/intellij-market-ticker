package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.DomesticAlternativePrice
import com.github.myeoungdev.marketticker.domain.model.DomesticTradeType
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.github.myeoungdev.marketticker.domain.model.PriceStatus
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.components.service

/**
 * 국내 종목의 원본 KRX 시세는 유지하고, 화면 표시용 현재가만 설정된 거래 기준에 맞춰 선택합니다.
 */
class DomesticDisplayPriceSelector(
    private val settingsService: AppSettingsService = service(),
    private val domesticTradeTypeResolver: (AppSettingsService.DomesticTradeVenueMode) -> DomesticTradeType =
        DomesticTradeTypeResolver::resolve
) {

    fun select(price: TickerPrice): TickerPrice {
        if (!price.marketType.isKoreanMarket()) {
            return price
        }

        val mode = settingsService.getDomesticTradeVenueMode()
        return when (domesticTradeTypeResolver(mode)) {
            DomesticTradeType.KRX -> price
            DomesticTradeType.NXT -> price.withAlternativePrice(mode)
        }
    }

    private fun TickerPrice.withAlternativePrice(mode: AppSettingsService.DomesticTradeVenueMode): TickerPrice {
        val alternative = overMarketPrice?.takeIf { it.currentPrice > 0.0 } ?: return this
        if (mode == AppSettingsService.DomesticTradeVenueMode.MIXED && alternative.marketStatus != MarketStatus.OPEN) {
            return this
        }
        return copy(
            currentPrice = alternative.currentPrice,
            openPrice = alternative.openPrice.takeIfPositiveOr(openPrice),
            highPrice = alternative.highPrice.takeIfPositiveOr(highPrice),
            lowPrice = alternative.lowPrice.takeIfPositiveOr(lowPrice),
            changeAmount = alternative.changeAmount,
            changeRate = alternative.changeRate,
            tradeVolume = alternative.tradeVolume.takeIfPositiveOr(tradeVolume),
            tradeValue = alternative.tradeValue.takeIfPositiveOr(tradeValue),
            marketStatus = alternative.marketStatus,
            priceStatus = alternative.toPriceStatus()
        )
    }

    private fun DomesticAlternativePrice.toPriceStatus(): PriceStatus {
        return when {
            changeRate > 0.0 -> PriceStatus.RISING
            changeRate < 0.0 -> PriceStatus.FALLING
            else -> PriceStatus.STEADY
        }
    }

    private fun Double.takeIfPositiveOr(fallback: Double): Double {
        return if (this > 0.0) this else fallback
    }

    private fun Long.takeIfPositiveOr(fallback: Long): Long {
        return if (this > 0L) this else fallback
    }
}
