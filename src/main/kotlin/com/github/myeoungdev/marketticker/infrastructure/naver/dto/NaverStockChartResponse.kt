package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.myeoungdev.marketticker.application.service.PriceHistoryService
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Naver 국내/해외 주식 차트 캔들 데이터입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverStockChartCandle(
    val localDate: String,
    val closePrice: Double,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val accumulatedTradingVolume: Long,
    val foreignRetentionRate: Double? = null
) {
    fun toPriceHistoryCandle(zoneId: ZoneId): PriceHistoryService.Candle {
        return PriceHistoryService.Candle(
            at = parseLocalDateToInstant(localDate, zoneId),
            open = openPrice,
            high = highPrice,
            low = lowPrice,
            close = closePrice,
            volume = accumulatedTradingVolume
        )
    }

    private fun parseLocalDateToInstant(raw: String, zoneId: ZoneId): Instant {
        return when (raw.length) {
            8 -> LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(zoneId).toInstant()
            12 -> LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMddHHmm")).atZone(zoneId).toInstant()
            14 -> LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).atZone(zoneId).toInstant()
            else -> LocalDate.parse(raw.take(8), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(zoneId).toInstant()
        }
    }
}
