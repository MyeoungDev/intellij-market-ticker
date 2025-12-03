package com.github.myeoungdev.marketticker.domain.model

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-03
 */
enum class CurrencyType(
    val code: String,
    val text: String,
    val unit: String,
    val uniCode: String
) {
    KRW(
        "KRW",
        "Republic of Korea won",
        "₩",
        "U+20A9"
    );

    fun of(code: String): CurrencyType {

        val currencyType = CurrencyType.entries.find { o -> o.code == code }
        requireNotNull(currencyType) { "Invalid currency type" }

        return currencyType
    }
}