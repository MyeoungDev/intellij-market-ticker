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
    val zoneId: ZoneId,
    val country: Country
) {
    KOSPI("KOSPI", "코스피", ZoneId.of("Asia/Seoul"), Country.KOREA),
    KOSDAQ("KOSDAQ", "코스닥", ZoneId.of("Asia/Seoul"), Country.KOREA),
    NASDAQ("NASDAQ", "나스닥", ZoneId.of("America/New_York"), Country.USA),
    NYSE("NYSE", "뉴욕증권거래소", ZoneId.of("America/New_York"), Country.USA),
    TOKYO("TSE", "도쿄증권거래소", ZoneId.of("Asia/Tokyo"), Country.JAPAN),
    UPBIT("UPBIT", "업비트", ZoneId.of("Asia/Seoul"), Country.KOREA),
    BITHUMB("BITHUMB", "빗썸", ZoneId.of("Asia/Seoul"), Country.KOREA),
    UNKNOWN("UNKNOWN", "알 수 없음", ZoneId.systemDefault(), Country.UNKNOWN);

    companion object {
        fun of(type: String): MarketType {
            return values().find { it.name == type } ?: UNKNOWN
        }
    }

    fun isKoreanMarket(): Boolean {
        return this == KOSPI || this == KOSDAQ
    }

    fun isCryptoMarket(): Boolean {
        return this == UPBIT || this == BITHUMB
    }
}
