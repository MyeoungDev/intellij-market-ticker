package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.PriceStatus
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import java.time.Instant

/**
 * Naver 코인 실시간 가격 응답 래퍼입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverRealtimeCoinPriceResponse(
    val pollingInterval: Long = 0,
    val time: String? = null,
    val datas: List<NaverCoinPrice> = emptyList()
)

/**
 * 코인 개별 시세 데이터입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverCoinPrice(
    val fqnfTicker: String,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val tradePrice: Double,
    val previousClosePrice: Double,
    val change: String,
    val changeRate: Double,
    val changeValue: Double,
    val accumulatedTradingVolume: Double,
    val accumulatedTradingValue: Double,
    val koreaTradedAt: String?
) {

    fun toTickerPrice(): TickerPrice {
        val parts = fqnfTicker.split("_")
        val symbol = parts.getOrElse(0) { fqnfTicker }
        val currencyCode = parts.getOrElse(1) { "KRW" }
        val exchange = parts.getOrElse(2) { "UPBIT" }

        return TickerPrice(
            symbol = symbol,
            tradingSymbol = fqnfTicker,
            name = symbol,
            previousClosePrice = previousClosePrice,
            openPrice = openPrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            currentPrice = tradePrice,
            priceStatus = PriceStatus.from(changeValue),
            changeAmount = changeValue,
            changeRate = changeRate,
            tradeVolume = accumulatedTradingVolume.toLong(),
            tradeValue = accumulatedTradingValue,
            marketStatus = MarketStatus.OPEN,
            marketType = MarketType.of(exchange),
            currency = CurrencyType.of(currencyCode),
            nationCode = "KOR",
            nationName = "대한민국"
        )
    }
}

/**
 * Naver 코인 차트 응답 래퍼입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverCryptoChartResponse(
    val isSuccess: Boolean = false,
    val detailCode: String? = null,
    val message: String? = null,
    val result: List<NaverCryptoCandle> = emptyList()
)

/**
 * Naver 코인 캔들 데이터입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverCryptoCandle(
    val candleId: String,
    val tradeBaseAt: String,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val closePrice: Double,
    val accumulatedTradingVolume: Double
) {
    fun toPriceHistoryCandle(): com.github.myeoungdev.marketticker.application.service.PriceHistoryService.Candle {
        return com.github.myeoungdev.marketticker.application.service.PriceHistoryService.Candle(
            at = Instant.parse(tradeBaseAt),
            open = openPrice,
            high = highPrice,
            low = lowPrice,
            close = closePrice,
            volume = accumulatedTradingVolume.toLong()
        )
    }
}
