package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 해외 종목 개요 응답입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverForeignStockOverview(
    val companyName: String? = null,
    val companyNameEng: String? = null,
    val summary: String? = null,
    val summaries: NaverForeignStockSummaryBlock? = null,
    val industry: NaverForeignStockIndustry? = null,
    val stockItemListedInfo: NaverForeignStockListedInfo? = null,
    val ownerInfoList: List<NaverForeignStockOwnerInfo> = emptyList()
)

/**
 * 해외 종목 회사 기본 정보 블록입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverForeignStockSummaryBlock(
    val summary: String? = null,
    val representativeName: String? = null,
    val representativeId: String? = null,
    val nation: String? = null,
    val employees: Int? = null,
    val employeesLastUpdated: String? = null,
    val city: String? = null,
    val address: String? = null,
    val url: String? = null
)

/**
 * 해외 종목 산업 정보입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverForeignStockIndustry(
    val code: String? = null,
    val industryGroupKor: String? = null,
    val name: String? = null
)

/**
 * 해외 종목 상장 정보입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverForeignStockListedInfo(
    val stockExchangeType: StockExchangeType? = null,
    val stockExchange: String? = null,
    val currency: String? = null,
    val accountDate: String? = null,
    val listedAt: String? = null,
    val countOfListedStock: Long? = null,
    val marketValue: String? = null,
    val marketValueKrw: String? = null,
    val marketValueFull: String? = null
)

/**
 * 해외 종목 주요 주주 정보입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverForeignStockOwnerInfo(
    val investorName: String? = null,
    val position: Long? = null,
    val outstanding: String? = null
)
