package com.github.myeoungdev.marketticker.domain.model

/**
 * 시장 지표 요약 정보를 표현하는 도메인 모델입니다.
 */
data class MarketIndicator(
    val code: String,
    val name: String,
    val currentPrice: Double,
    val changeRate: Double,
    val marketStatus: MarketStatus,
    val category: IndicatorCategory,
    val unit: String? = null
)

/**
 * 시장 지표 카테고리입니다.
 */
enum class IndicatorCategory {
    DOMESTIC_INDEX,
    WORLD_INDEX,
    METAL,
    ENERGY,
    EXCHANGE_RATE
}
