package com.github.myeoungdev.marketticker.domain.model.screener

import com.github.myeoungdev.marketticker.domain.model.Ticker

enum class ScreenerPreset(
    val signalCode: String,
    val labelKo: String,
    val labelEn: String
) {
    SEARCH_TOP("SEARCH_TOP", "검색 상위", "Search Top"),
    PRICE_TOP("PRICE_TOP", "거래대금 상위", "Value Top"),
    UP("UP", "상승 상위", "Top Rising"),
    DOWN("DOWN", "하락 상위", "Top Falling");
}

data class ScreenedTicker(
    val ticker: Ticker,
    val sector: String,
    val industry: String,
    val exchange: String,
    val marketCap: String,
    val pe: String,
    val price: String,
    val change: String,
    val volume: String,
    val signalLabel: String
)
