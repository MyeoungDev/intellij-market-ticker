package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * 네이버의 종목 검색에 대한 Item 정의 클래스
 *
 * @author  : 강명관
 * @since   : 2025-11-30
 */
data class NaverSearchItem(
    val code: String,        // "005930"
    val name: String,        // "삼성전자"
    val typeCode: String,    // "KOSPI"
    val typeName: String,    // "코스피"
    val url: String,         // "/domestic/stock/005930/total"
    val reutersCode: String, // "005930"
    val nationCode: String?,  // "KOR"
    val nationName: String?,  // "대한민국"
    val category: String     // "stock"
) {
    fun toTicker(): Ticker {
        return Ticker(
            symbol = code,
            name = name,
            marketCode = typeCode,
            marketName = typeName,
            nationCode = nationCode,
            nationName = nationName
        )
    }
}