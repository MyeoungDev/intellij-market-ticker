package com.github.myeoungdev.marketticker.application.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PortfolioInputFormatterTest {

    @Test
    fun `큰 숫자는 지수 표기 없이 일반 숫자 문자열로 포맷한다`() {
        assertThat(PortfolioInputFormatter.format(15_000_000.0)).isEqualTo("15000000")
        assertThat(PortfolioInputFormatter.format(1.5E7)).isEqualTo("15000000")
    }

    @Test
    fun `작은 숫자는 지수 표기 없이 일반 숫자 문자열로 포맷한다`() {
        assertThat(PortfolioInputFormatter.format(0.000001)).isEqualTo("0.000001")
    }

    @Test
    fun `불필요한 소수점 끝자리 0은 제거한다`() {
        assertThat(PortfolioInputFormatter.format(123.4500)).isEqualTo("123.45")
        assertThat(PortfolioInputFormatter.format(10.0)).isEqualTo("10")
    }

    @Test
    fun `쉼표가 포함된 입력값을 숫자로 변환한다`() {
        assertThat(PortfolioInputFormatter.parse("15,000,000")).isEqualTo(15_000_000.0)
        assertThat(PortfolioInputFormatter.parse("1,234.56")).isEqualTo(1_234.56)
    }

    @Test
    fun `빈 값과 유효하지 않거나 양수가 아닌 입력값은 null을 반환한다`() {
        assertThat(PortfolioInputFormatter.parse("")).isNull()
        assertThat(PortfolioInputFormatter.parse("abc")).isNull()
        assertThat(PortfolioInputFormatter.parse("0")).isNull()
        assertThat(PortfolioInputFormatter.parse("-1")).isNull()
        assertThat(PortfolioInputFormatter.parse("NaN")).isNull()
        assertThat(PortfolioInputFormatter.parse("Infinity")).isNull()
    }
}
