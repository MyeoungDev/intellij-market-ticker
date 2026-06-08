package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 네이버 국내 주식 마켓/스크리너 응답의 개별 종목 행입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDomesticMarketStockItem(
    val itemcode: String? = "",
    val itemname: String? = "",
    val sosok: String? = "",
    val marketStatus: String? = "",
    val nowPrice: String? = "",
    val prevChangeRate: String? = "",
    val tradeVolume: String? = "",
    val tradeAmount: String? = "",
    val marketSum: String? = "",
    val per: String? = "",
    val listedDate: String? = null
)
