package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * Naver 에서 사용되는 공통 응답 Wrapper
 *
 * @author  : 강명관
 * @since   : 2025-11-30
 */
data class NaverSearchResponse(
    val isSuccess: Boolean,
    val detailCode: String?,
    val message: String?,
    val result: SearchResultPayload?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResultPayload(
    val query: String,
    val items: List<NaverSearchItem> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverSearchItem(
    val code: String,        // "005930"
    val name: String,        // "삼성전자"
    val typeCode: String,    // "KOSPI"
    val typeName: String,    // "코스피"
    val url: String,         // "/domestic/stock/005930/total"
    // TODO: 해외 주식의 경우 이 reutersCode 를 사용해야 함
    val reutersCode: String, // "005930"
    val nationCode: String?,  // "KOR"
    val nationName: String?,  // "대한민국"
    val category: String     // "stock"
) {
    fun toTicker(): Ticker {
        return Ticker(
            symbol = code,
            name = name,
            marketType = MarketType.of(typeCode),
            nationCode = nationCode,
            nationName = nationName
        )
    }
}