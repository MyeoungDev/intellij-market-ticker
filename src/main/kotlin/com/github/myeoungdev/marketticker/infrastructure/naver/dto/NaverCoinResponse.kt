package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.PriceStatus
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import java.time.LocalDateTime
import java.time.ZoneId
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

/**
 * Naver 코인 상세 응답입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverCoinOverview(
    val fqnfTicker: String,
    val nfTicker: String,
    val exchangeTicker: String,
    val krName: String,
    val enName: String? = null,
    val exchangeType: String,
    val exchangeName: String? = null,
    val tradePrice: Double,
    val change: String,
    val changeRate: Double,
    val changeValue: Double,
    val koreaTradedAt: String,
    val krwPremiumRate: Double? = null,
    val totalInfos: List<NaverCoinOverviewInfo> = emptyList(),
    val profileInfo: NaverCoinProfileInfo? = null
) {

    /**
     * 코인 상세 정보를 오늘 일봉 캔들로 변환합니다.
     */
    fun toDailyCandle(zoneId: ZoneId): com.github.myeoungdev.marketticker.application.service.PriceHistoryService.Candle {
        val tradedAt = LocalDateTime.parse(koreaTradedAt).atZone(zoneId).toInstant()
        val openPrice = totalInfoValue("openPrice") ?: tradePrice
        val highPrice = totalInfoValue("highPrice") ?: tradePrice
        val lowPrice = totalInfoValue("lowPrice") ?: tradePrice
        val volume = (totalInfoValue("accumulatedTradingVolume") ?: 0.0).toLong()

        return com.github.myeoungdev.marketticker.application.service.PriceHistoryService.Candle(
            at = tradedAt,
            open = openPrice,
            high = highPrice,
            low = lowPrice,
            close = tradePrice,
            volume = volume
        )
    }

    /**
     * 기존 일봉 목록의 마지막 캔들을 오늘 상세 시세로 보정합니다.
     */
    fun mergeDailyCandles(
        candles: List<com.github.myeoungdev.marketticker.application.service.PriceHistoryService.Candle>,
        zoneId: ZoneId
    ): List<com.github.myeoungdev.marketticker.application.service.PriceHistoryService.Candle> {
        val overviewCandle = toDailyCandle(zoneId)
        if (candles.isEmpty()) {
            return listOf(overviewCandle)
        }

        val overviewDate = overviewCandle.at.atZone(zoneId).toLocalDate()
        val merged = candles.toMutableList()
        val lastDate = merged.last().at.atZone(zoneId).toLocalDate()

        if (lastDate == overviewDate) {
            merged[merged.lastIndex] = overviewCandle.copy(
                at = merged.last().at
            )
        } else if (lastDate.isBefore(overviewDate)) {
            merged += overviewCandle
        }

        return merged
    }

    private fun totalInfoValue(code: String): Double? = totalInfos.firstOrNull { it.code == code }?.value
}

/**
 * 코인 프로필 정보입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverCoinProfileInfo(
    val contentKr: String? = null,
    val initialIssueDate: String? = null,
    val maxSupply: Double? = null,
    val totalSupply: Double? = null,
    val marketCap: Double? = null,
    val circulatingSupply: Double? = null,
    val accumulatedTradingVolume24h: Double? = null,
    val accumulatedTradingValue24h: Double? = null,
    val symbolImageUrl: String? = null
)

/**
 * Naver 코인 상세 지표 항목입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverCoinOverviewInfo(
    val code: String,
    val key: String? = null,
    val value: Double? = null
)
