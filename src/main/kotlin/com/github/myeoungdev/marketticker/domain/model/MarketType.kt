package com.github.myeoungdev.marketticker.domain.model

import java.time.ZoneId

/**
 * 내부 시스템에서 사용할 표준 거래소(Market) 모델
 *
 * @author  : 강명관
 * @since   : 2025-12-03
 */
enum class MarketType(
    val code: String,
    val displayName: String,
    val displayNameEn: String,
    val zoneId: ZoneId,
    val country: Country
) {
    KOREA("KOREA", "국내 주식", "Domestic", ZoneId.of("Asia/Seoul"), Country.KOREA),
    KOSPI("KOSPI", "코스피", "KOSPI", ZoneId.of("Asia/Seoul"), Country.KOREA),
    KOSDAQ("KOSDAQ", "코스닥", "KOSDAQ", ZoneId.of("Asia/Seoul"), Country.KOREA),
    USA("USA", "미국 주식", "USA", ZoneId.of("America/New_York"), Country.USA),
    NASDAQ("NASDAQ", "나스닥", "NASDAQ", ZoneId.of("America/New_York"), Country.USA),
    NYSE("NYSE", "뉴욕증권거래소", "NYSE", ZoneId.of("America/New_York"), Country.USA),
    TOKYO("TSE", "도쿄증권거래소", "Tokyo Stock Exchange", ZoneId.of("Asia/Tokyo"), Country.JAPAN),
    SHANGHAI("SHH", "중국거래소", "Shanghai Stock Exchange", ZoneId.of("Asia/Shanghai"), Country.CHINA),
    HONG_KONG("HKG", "홍콩거래소", "Hong Kong Exchange", ZoneId.of("Asia/Hong_Kong"), Country.HONG_KONG),
    VIETNAM("VNM", "베트남거래소", "Vietnam Exchange", ZoneId.of("Asia/Ho_Chi_Minh"), Country.VIETNAM),
    UPBIT("UPBIT", "업비트", "Upbit", ZoneId.of("Asia/Seoul"), Country.KOREA),
    BITHUMB("BITHUMB", "빗썸", "Bithumb", ZoneId.of("Asia/Seoul"), Country.KOREA),
    UNKNOWN("UNKNOWN", "알 수 없음", "Unknown", ZoneId.systemDefault(), Country.UNKNOWN);

    companion object {
        fun of(type: String): MarketType {
            val normalized = type.trim()
            return values().find {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.code.equals(normalized, ignoreCase = true)
            } ?: when (normalized.uppercase()) {
                "DOMESTIC", "KR", "KOR", "KRX" -> KOREA
                "US" -> USA
                "NSQ" -> NASDAQ
                "NYS" -> NYSE
                "TYO" -> TOKYO
                "JPN" -> TOKYO
                "CHN", "SSE", "SHH" -> SHANGHAI
                "HKG", "HKEX" -> HONG_KONG
                "VNM", "HSX", "HOSE", "HNX" -> VIETNAM
                else -> UNKNOWN
            }
        }
    }

    fun isKoreanMarket(): Boolean {
        return this == KOREA || this == KOSPI || this == KOSDAQ
    }

    fun isGlobalStockMarket(): Boolean {
        return this in setOf(USA, NASDAQ, NYSE, TOKYO, SHANGHAI, HONG_KONG, VIETNAM)
    }

    fun isCryptoMarket(): Boolean {
        return this == UPBIT || this == BITHUMB
    }

    fun nativeCurrency(): CurrencyType {
        return when (this) {
            KOREA, KOSPI, KOSDAQ, UPBIT, BITHUMB -> CurrencyType.KRW
            USA, NASDAQ, NYSE -> CurrencyType.USD
            TOKYO -> CurrencyType.JPY
            SHANGHAI -> CurrencyType.CNY
            HONG_KONG -> CurrencyType.HKD
            VIETNAM -> CurrencyType.UNKNOWN
            UNKNOWN -> CurrencyType.UNKNOWN
        }
    }
}
