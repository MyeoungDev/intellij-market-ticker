package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.myeoungdev.marketticker.common.extenion.parseCommaToDouble
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import kotlin.math.abs

/**
 * Naver 시장 지표 풀링 응답입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverMarketIndicatorResponse(
    val pollingInterval: Long = 0,
    val time: String? = null,
    val datas: List<NaverMarketIndicatorItem> = emptyList()
)

/**
 * Naver 시장 지표 단일 항목입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverMarketIndicatorItem(
    val itemCode: String? = null,
    val symbolCode: String? = null,
    val reutersCode: String? = null,
    val stockName: String? = null,
    val name: String? = null,
    val closePrice: String,
    val fluctuationsRatio: String,
    val compareToPreviousClosePrice: String? = null,
    val fluctuations: String? = null,
    val marketStatus: String,
    val unit: String? = null
) {

    fun toMarketIndicator(category: IndicatorCategory): MarketIndicator {
        val code = itemCode ?: symbolCode ?: reutersCode ?: "UNKNOWN"
        val title = stockName ?: name ?: code
        val close = closePrice.parseCommaToDouble()
        val ratio = parseChangeRate(close)

        return MarketIndicator(
            code = code,
            name = title,
            currentPrice = close,
            changeRate = ratio,
            marketStatus = MarketStatus.of(marketStatus),
            category = category,
            unit = unit
        )
    }

    private fun parseChangeRate(close: Double): Double {
        val parsedRatio = fluctuationsRatio.parseCommaToDouble()
        if (parsedRatio != 0.0) {
            return parsedRatio
        }

        // fluctuationsRatio 값이 비어있거나 파싱 실패한 경우 전일 대비로 백업 계산
        val delta = compareToPreviousClosePrice.parseCommaToDouble()
        if (delta == 0.0) {
            return 0.0
        }
        val previousClose = close - delta
        if (previousClose == 0.0) {
            return 0.0
        }
        val ratio = (delta / abs(previousClose)) * 100.0
        return if (ratio.isFinite()) ratio else 0.0
    }
}
