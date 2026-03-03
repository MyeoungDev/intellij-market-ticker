package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.components.Service
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * 실시간 가격 스트림을 메모리에 누적하고, 차트용 캔들/이동평균 데이터를 생성하는 서비스입니다.
 */
@Service(Service.Level.APP)
class PriceHistoryService {

    /**
     * 차트 조회 기간입니다.
     */
    enum class Period {
        DAY,
        WEEK,
        MONTH
    }

    /**
     * 원시 가격 포인트입니다.
     */
    data class PricePoint(
        val at: Instant,
        val price: Double,
        val volume: Long
    )

    /**
     * 집계된 OHLCV 캔들 데이터입니다.
     */
    data class Candle(
        val at: Instant,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Long
    )

    private val maxPointsPerSymbol = 4_000
    private val history = ConcurrentHashMap<String, MutableList<PricePoint>>()

    /**
     * 현재 시점 가격들을 심볼별 히스토리에 추가합니다.
     */
    fun append(prices: List<TickerPrice>) {
        val now = Instant.now()
        prices.forEach { price ->
            val key = toKey(price.symbol, price.marketType.name)
            val points = history.computeIfAbsent(key) { mutableListOf() }
            points.add(PricePoint(now, price.currentPrice, price.tradeVolume))
            if (points.size > maxPointsPerSymbol) {
                points.subList(0, points.size - maxPointsPerSymbol).clear()
            }
        }
    }

    /**
     * 지정 심볼과 기간에 대한 캔들 데이터를 생성합니다.
     */
    fun buildCandles(symbol: String, marketType: String, period: Period, zoneId: ZoneId): List<Candle> {
        val key = toKey(symbol, marketType)
        val points = history[key].orEmpty()
        if (points.isEmpty()) return emptyList()

        val now = Instant.now()
        val (from, bucketMinutes) = when (period) {
            Period.DAY -> now.minus(Duration.ofHours(24)) to 5L
            Period.WEEK -> now.minus(Duration.ofDays(7)) to 60L
            Period.MONTH -> now.minus(Duration.ofDays(30)) to (60L * 24L)
        }

        val filtered = points.filter { it.at >= from }
        if (filtered.isEmpty()) return emptyList()

        val grouped = linkedMapOf<Instant, MutableList<PricePoint>>()
        filtered.forEach { point ->
            val zdt = ZonedDateTime.ofInstant(point.at, zoneId)
            val truncated = zdt.truncatedTo(ChronoUnit.MINUTES)
            val minute = truncated.minute.toLong()
            val flooredMinute = minute - (minute % bucketMinutes)
            val bucket = truncated.withMinute(flooredMinute.toInt()).withSecond(0).withNano(0).toInstant()
            grouped.computeIfAbsent(bucket) { mutableListOf() }.add(point)
        }

        return grouped.entries.map { (bucket, bucketPoints) ->
            val open = bucketPoints.first().price
            val close = bucketPoints.last().price
            val high = bucketPoints.maxOf { it.price }
            val low = bucketPoints.minOf { it.price }
            val volume = (bucketPoints.last().volume - bucketPoints.first().volume).coerceAtLeast(0)
            Candle(bucket, open, high, low, close, volume)
        }
    }

    /**
     * 단순 이동평균(SMA)을 계산합니다.
     *
     * 윈도우 길이보다 데이터가 적은 구간은 `null`로 반환합니다.
     */
    fun movingAverage(candles: List<Candle>, window: Int): List<Double?> {
        if (window <= 1) return candles.map { it.close }

        val result = mutableListOf<Double?>()
        var runningSum = 0.0

        candles.forEachIndexed { index, candle ->
            runningSum += candle.close
            if (index >= window) {
                runningSum -= candles[index - window].close
            }
            result += if (index >= window - 1) runningSum / window else null
        }

        return result
    }

    private fun toKey(symbol: String, marketType: String): String = "${symbol.uppercase()}::$marketType"
}
