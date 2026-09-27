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
    val indexName: String? = null,
    val name: String? = null,
    val closePrice: String,
    val fluctuationsRatio: String,
    val compareToPreviousClosePrice: String? = null,
    val fluctuations: String? = null,
    val marketStatus: String? = null,
    val unit: String? = null
) {

    fun toMarketIndicator(category: IndicatorCategory): MarketIndicator {
        val code = itemCode ?: symbolCode ?: reutersCode ?: "UNKNOWN"
        val title = stockName ?: indexName ?: name ?: code
        val close = closePrice.parseCommaToDouble()
        val ratio = parseChangeRate(close)

        return MarketIndicator(
            code = code,
            name = title,
            currentPrice = close,
            changeRate = ratio,
            marketStatus = MarketStatus.of(marketStatus.orEmpty()),
            category = category,
            unit = unit
        )
    }

    private fun parseChangeRate(close: Double): Double {
        val parsedRatio = fluctuationsRatio.parseCommaToDouble()
        if (parsedRatio != 0.0) {
            return parsedRatio
        }

        // fluctuationsRatio 값이 비어있거나 파싱 실패한 경우 전일 대비/등락폭으로 백업 계산
        val delta = compareToPreviousClosePrice.parseCommaToDouble().takeIf { it != 0.0 }
            ?: fluctuations.parseCommaToDouble()
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

/**
 * Naver 환전고시 환율 단일 항목입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverExchangeRateItem(
    val marketIndexCd: String? = null,
    val name: String? = null,
    val fullName: String? = null,
    val symbol: String? = null,
    val saleBaseRate: String? = null,
    val changeRate: String = "0",
    val changeVal: String? = null,
    val marketStatus: String = "",
    val currencyInfo: NaverCurrencyInfo? = null,
) {

    fun toMarketIndicator(): MarketIndicator {
        val code = normalizedMarketIndexCode() ?: symbol ?: currencyInfo?.currencyCode ?: "UNKNOWN"
        val title = symbol ?: name ?: currencyInfo?.currencyKoreanName ?: code

        return MarketIndicator(
            code = code,
            name = title,
            currentPrice = requireNotNull(saleBaseRate).parseCommaToDouble(),
            changeRate = changeRate.parseCommaToDouble(),
            marketStatus = MarketStatus.of(marketStatus),
            category = IndicatorCategory.EXCHANGE_RATE,
            unit = "KRW"
        )
    }

    fun normalizedMarketIndexCode(): String? {
        return marketIndexCd?.takeIf { it.isNotBlank() }?.let(::toFxCode)
            ?: currencyInfo?.currencyCode?.takeIf { it.isNotBlank() }?.let(::toFxCode)
            ?: symbol?.takeIf { it.isNotBlank() }?.let(::toFxCode)
    }

    private fun toFxCode(value: String): String {
        val normalized = value.trim().uppercase()
        return when {
            normalized.startsWith("FX_") -> normalized
            normalized.endsWith("KRW") -> "FX_$normalized"
            else -> "FX_${normalized}KRW"
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverCurrencyInfo(
    val currencyCode: String? = null,
    val nationKoreanName: String? = null,
    val currencyKoreanName: String? = null,
    val currencyBasis: Double? = null
)
