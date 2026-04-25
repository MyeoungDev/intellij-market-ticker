package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarketIndicatorPriceFormatterTest {

    @Test
    fun `원자재 단위는 raw 문자열로 유지한다`() {
        val result = formatIndicatorPrice(
            value = 5081.2,
            unit = "USD/OZS",
            formatDecimal = { value, _ -> value.toString() },
            formatAmount = { _, _, _ -> error("should not be called") }
        )

        assertThat(result).isEqualTo("5081.2 USD/OZS")
    }

    @Test
    fun `통화 단위는 기존 포맷 경로를 사용한다`() {
        val result = formatIndicatorPrice(
            value = 1477.6,
            unit = "USD",
            formatDecimal = { value, _ -> value.toString() },
            formatAmount = { value, currency, _ ->
                "${value.toInt()} ${currency?.code}"
            }
        )

        assertThat(result).isEqualTo("1477 USD")
    }
}
