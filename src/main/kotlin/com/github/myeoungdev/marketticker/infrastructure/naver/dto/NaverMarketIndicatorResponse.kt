package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.myeoungdev.marketticker.common.extenion.parseCommaToDouble
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.MarketStatus

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

        return MarketIndicator(
            code = code,
            name = title,
            currentPrice = closePrice.parseCommaToDouble(),
            changeRate = fluctuationsRatio.parseCommaToDouble(),
            marketStatus = MarketStatus.of(marketStatus),
            category = category,
            unit = unit
        )
    }
}
