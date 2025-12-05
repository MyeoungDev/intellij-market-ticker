package com.github.myeoungdev.marketticker.infrastructure.naver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
class NaverMapperTest {

    @Test
    fun `네이버 검색 결과를 Ticker 도메인 모델로 변환한다`() {
        // Given
        val naverItem = NaverSearchItem(
            code = "005930",
            name = "삼성전자",
            typeCode = "KOSPI",
            typeName = "코스피",
            url = "/...",
            reutersCode = "005930",
            nationCode = "KOR",
            nationName = "대한민국",
            category = "stock"
        )

        // When
        val ticker = naverItem.toTicker()

        // Then
        assertThat("005930").isEqualTo(ticker.symbol)
        assertThat("삼성전자").isEqualTo(ticker.name)
        assertThat("KOSPI").isEqualTo(ticker.marketType.code)
        assertThat("KOR").isEqualTo(ticker.nationCode)
        assertThat("대한민국").isEqualTo(ticker.nationName)
    }
}