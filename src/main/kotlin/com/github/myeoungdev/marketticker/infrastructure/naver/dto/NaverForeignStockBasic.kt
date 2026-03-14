package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 해외 종목 기본 시세/지표 응답입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverForeignStockBasic(
    val stockEndType: String? = null,
    val reutersCode: String? = null,
    val stockName: String? = null,
    val stockNameEng: String? = null,
    val symbolCode: String? = null,
    val stockExchangeType: StockExchangeType? = null,
    val stockExchangeName: String? = null,
    val industryCodeType: NaverForeignStockIndustry? = null,
    val closePrice: String? = null,
    val compareToPreviousClosePrice: String? = null,
    val fluctuationsRatio: String? = null,
    val localTradedAt: String? = null,
    val marketStatus: String? = null,
    val currencyType: CurrencyResponse? = null,
    val countOfListedStock: Long? = null,
    val endUrl: String? = null,
    val stockItemTotalInfos: List<NaverForeignStockMetric> = emptyList()
)

/**
 * 해외 종목 핵심 지표 항목입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverForeignStockMetric(
    val code: String? = null,
    val key: String? = null,
    val keyDesc: String? = null,
    val value: String? = null,
    val valueDesc: String? = null
)
