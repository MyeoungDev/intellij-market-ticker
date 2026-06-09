package com.github.myeoungdev.marketticker.domain.model

/**
 * 국내 종목의 조회 기준 거래소입니다.
 *
 * KOSPI/KOSDAQ 같은 상장 시장은 [MarketType]이 담당하고,
 * KRX/NXT 같은 거래 체결 기준은 이 타입으로 분리합니다.
 */
enum class DomesticTradeType(
    val code: String,
    val labelKo: String,
    val labelEn: String
) {
    KRX("KRX", "KRX", "KRX"),
    NXT("NXT", "NXT", "NXT");

    companion object {
        fun of(code: String?): DomesticTradeType {
            return values().firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: KRX
        }
    }
}
