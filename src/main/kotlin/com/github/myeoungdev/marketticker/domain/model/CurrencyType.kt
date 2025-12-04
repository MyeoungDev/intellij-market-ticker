package com.github.myeoungdev.marketticker.domain.model

/**
 * 각 국제 통화를 정의한 통화 타입
 *
 * @author  : 강명관
 * @since   : 2025-12-03
 */
enum class CurrencyType(
    val code: String,
    val text: String,
    val symbol: String,
    val unicode: String
) {
    KRW(
        code = "KRW",
        text = "Republic of Korea won",
        symbol = "₩",
        unicode = "U+20A9"
    ),
    USD(
        code = "USD",
        text = "United States Dollar",
        symbol = "$",
        unicode = "U+0024"
    ),
    JPY(
        code = "JPY",
        text = "Japanese Yen",
        symbol = "¥",
        unicode = "U+00A5"
    ),
    CNY(
        code = "CNY",
        text = "Chinese Yuan",
        symbol = "¥",
        unicode = "U+00A5"
    ),
    EUR(
        code = "EUR",
        text = "Euro",
        symbol = "€",
        unicode = "U+20AC"
    ),
    UNKNOWN(
        code = "UNKNOWN",
        text = "Unknown Currency",
        symbol = "",
        unicode = ""
    );

    companion object {
        fun of(code: String): CurrencyType {
            if (code.isBlank()) {
                return UNKNOWN
            }

            return entries.find { it.code.equals(code, ignoreCase = true) }
                ?: UNKNOWN
        }

        fun fromSymbol(symbol: String): CurrencyType {
            return entries.find { it.symbol == symbol } ?: UNKNOWN
        }
    }
}