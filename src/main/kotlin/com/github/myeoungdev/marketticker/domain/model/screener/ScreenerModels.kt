package com.github.myeoungdev.marketticker.domain.model.screener

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker

enum class ScreenerPreset(
    val signalCode: String,
    val labelKo: String,
    val labelEn: String
) {
    SEARCH_TOP("SEARCH_TOP", "검색 상위", "Search Top"),
    PRICE_TOP("PRICE_TOP", "거래대금 상위", "Value Top"),
    MARKET_CAP("MARKET_CAP", "시가총액 상위", "Market Cap"),
    UP("UP", "상승 상위", "Top Rising"),
    DOWN("DOWN", "하락 상위", "Top Falling");
}

private val STOCK_SCREENER_PRESETS = listOf(
    ScreenerPreset.SEARCH_TOP,
    ScreenerPreset.PRICE_TOP,
    ScreenerPreset.MARKET_CAP,
    ScreenerPreset.UP,
    ScreenerPreset.DOWN
)

private val CRYPTO_SCREENER_PRESETS = listOf(
    ScreenerPreset.SEARCH_TOP,
    ScreenerPreset.MARKET_CAP,
    ScreenerPreset.UP,
    ScreenerPreset.DOWN
)

fun MarketType.Companion.screenerMarkets(): List<MarketType> {
    return listOf(
        MarketType.KOREA,
        MarketType.USA,
        MarketType.SHANGHAI,
        MarketType.HONG_KONG,
        MarketType.TOKYO,
        MarketType.UPBIT,
        MarketType.BITHUMB
    )
}

fun MarketType.availableScreenerPresets(): List<ScreenerPreset> {
    return when (this) {
        MarketType.KOREA,
        MarketType.USA,
        MarketType.SHANGHAI,
        MarketType.HONG_KONG,
        MarketType.TOKYO,
        MarketType.KOSPI,
        MarketType.KOSDAQ,
        MarketType.NASDAQ,
        MarketType.NYSE -> STOCK_SCREENER_PRESETS

        MarketType.VIETNAM -> emptyList()

        MarketType.UPBIT,
        MarketType.BITHUMB -> CRYPTO_SCREENER_PRESETS

        MarketType.UNKNOWN -> emptyList()
    }
}

fun MarketType.screenerLabelKo(): String {
    return when (this) {
        MarketType.KOREA -> "국내 주식"
        MarketType.USA -> "미국 주식"
        MarketType.SHANGHAI -> "중국"
        MarketType.HONG_KONG -> "홍콩"
        MarketType.TOKYO -> "일본"
        MarketType.VIETNAM -> "베트남"
        else -> displayName
    }
}

fun MarketType.screenerLabelEn(): String {
    return when (this) {
        MarketType.KOREA -> "Domestic"
        MarketType.SHANGHAI -> "China"
        MarketType.HONG_KONG -> "Hong Kong"
        MarketType.TOKYO -> "Japan"
        MarketType.VIETNAM -> "Vietnam"
        else -> displayNameEn
    }
}

fun MarketType.screenerPresetLabelKo(preset: ScreenerPreset): String {
    return when (preset) {
        ScreenerPreset.SEARCH_TOP -> when (this) {
            MarketType.KOREA -> "검색 상위"
            MarketType.UPBIT, MarketType.BITHUMB -> "거래 상위"
            else -> "거래량 상위"
        }
        ScreenerPreset.PRICE_TOP -> "거래대금 상위"
        ScreenerPreset.MARKET_CAP -> "시가총액 상위"
        ScreenerPreset.UP -> "상승 상위"
        ScreenerPreset.DOWN -> "하락 상위"
    }
}

fun MarketType.screenerPresetLabelEn(preset: ScreenerPreset): String {
    return when (preset) {
        ScreenerPreset.SEARCH_TOP -> when (this) {
            MarketType.KOREA -> "Search Top"
            MarketType.UPBIT, MarketType.BITHUMB -> "Trading Top"
            else -> "Volume Top"
        }
        ScreenerPreset.PRICE_TOP -> "Value Top"
        ScreenerPreset.MARKET_CAP -> "Market Cap"
        ScreenerPreset.UP -> "Top Rising"
        ScreenerPreset.DOWN -> "Top Falling"
    }
}

data class ScreenedTicker(
    val ticker: Ticker,
    val sector: String,
    val industry: String,
    val exchange: String,
    val marketCap: String,
    val pe: String,
    val price: String,
    val changeRate: String,
    val changeAmount: String,
    val volume: String,
    val signalLabel: String
)
