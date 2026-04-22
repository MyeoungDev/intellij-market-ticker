package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverCoinRankResponse(
    val contents: List<NaverCoinRankItem> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverCoinRankItem(
    val fqnfTicker: String = "",
    val nfTicker: String = "",
    val exchangeTicker: String = "",
    val krName: String = "",
    val enName: String = "",
    val exchangeType: String = "",
    val exchangeName: String = "",
    val tradePrice: Double = 0.0,
    val change: String = "",
    val changeRate: Double = 0.0,
    val changeValue: Double = 0.0,
    val marketCap: Double = 0.0,
    val accumulatedTradingVolume: Double = 0.0,
    val accumulatedTradingValue: Double = 0.0,
    val koreaTradedAt: String = "",
    val krwPremiumRate: Double? = null,
    val isDelisting: Boolean = false
)
