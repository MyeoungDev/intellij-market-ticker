package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 네이버 국내 종목 상세 API의 핵심 지표/기업 설명 응답입니다.
 *
 * 하단 뉴스 패널에서 국내 종목 개요 카드를 만들 때 사용합니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDomesticStockDetail(
    val itemcode: String? = null,
    val itemname: String? = null,
    val marketStatus: String? = null,
    val nowVal: String? = null,
    val changeRate: String? = null,
    val marketSum: String? = null,
    val per: String? = null,
    val eps: String? = null,
    val pbr: String? = null,
    val bps: String? = null,
    val high52week: String? = null,
    val low52week: String? = null,
    val dividendRate: String? = null,
    val upJongName: String? = null,
    val comment1: String? = null,
    val comment2: String? = null,
    val comment3: String? = null
) {
    /**
     * comment1~3을 UI 표시에 적합한 단일 요약 문자열로 합칩니다.
     */
    fun summaryText(): String {
        return listOfNotNull(comment1, comment2, comment3)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
