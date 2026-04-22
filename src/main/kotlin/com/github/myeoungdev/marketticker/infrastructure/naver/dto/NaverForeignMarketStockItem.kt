package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverForeignMarketStockItem(
    val reutersCode: String = "",
    val symbolCode: String = "",
    val koreanCodeName: String = "",
    val englishCodeName: String = "",
    val reutersIndustryName: String = "",
    val marketValue: String = "",
    val currentPrice: String = "",
    val compareToPreviousClosePrice: String = "",
    val fluctuationsRatio: String = "",
    val accumulatedTradingVolume: String = "",
    val accumulatedTradingValue: String = "",
    val risefall: String = "",
    val stockExchangeType: NaverForeignMarketStockExchangeType = NaverForeignMarketStockExchangeType(),
    val currencyType: CurrencyResponse? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverForeignMarketStockExchangeType(
    val code: String = "",
    val zoneId: String = "",
    val delayTime: String = "",
    val nameKor: String = ""
)
