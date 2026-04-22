package com.github.myeoungdev.marketticker.extentions

import com.github.myeoungdev.marketticker.common.extenion.parseCommaToDouble
import com.github.myeoungdev.marketticker.common.extenion.parseCommaToLong
import com.github.myeoungdev.marketticker.common.extenion.toCommaString
import com.github.myeoungdev.marketticker.common.extenion.withCurrencyCode
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-04
 */
class FormatExtensionsTest {

    @Test
    fun `문자열을 Double로 변환할 때 다양한 형식을 처리한다`() {
        assertThat("1,234.56".parseCommaToDouble()).isEqualTo(1234.56)
        assertThat("-1,234".parseCommaToDouble()).isEqualTo(-1234.0)
        assertThat("1,,234".parseCommaToDouble()).isEqualTo(1234.0)
        assertThat("+1.23%".parseCommaToDouble()).isEqualTo(1.23)
        assertThat("−4.34%".parseCommaToDouble()).isEqualTo(-4.34)

        // 실패 케이스
        assertThat("invalid".parseCommaToDouble()).isEqualTo(0.0)
        assertThat(null.parseCommaToDouble()).isEqualTo(0.0)
    }

    @Test
    fun `문자열을 Long으로 변환할 때 소수점은 버린다`() {
        assertThat("1,234".parseCommaToLong()).isEqualTo(1234L)
        assertThat("1,234.99".parseCommaToLong()).isEqualTo(1234L)

        // 실패 케이스
        assertThat("".parseCommaToLong()).isEqualTo(0L)
    }

    @Test
    fun `Double 포맷팅 시 소수점 처리(반올림, 제거)를 확인한다`() {
        // 정수형 Double
        assertThat(1234.0.toCommaString()).isEqualTo("1,234")

        // 소수점 유지
        assertThat(1234.5.toCommaString()).isEqualTo("1,234.5")

        // 반올림 (DecimalFormat 기본 동작)
        assertThat(1234.556.toCommaString()).isEqualTo("1,234.56")
        assertThat(1234.554.toCommaString()).isEqualTo("1,234.55")
    }

    @Test
    fun `현재가 문자열 뒤에 통화 코드를 덧붙인다`() {
        assertThat("1,234.56".withCurrencyCode(CurrencyType.USD)).isEqualTo("1,234.56 USD")
        assertThat("72,000".withCurrencyCode(CurrencyType.KRW)).isEqualTo("72,000 KRW")
        assertThat("1,234.56".withCurrencyCode(CurrencyType.UNKNOWN)).isEqualTo("1,234.56")
        assertThat("1,234.56".withCurrencyCode(null)).isEqualTo("1,234.56")
    }
}
