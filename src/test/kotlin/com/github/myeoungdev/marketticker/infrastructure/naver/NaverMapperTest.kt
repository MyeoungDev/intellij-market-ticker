package com.github.myeoungdev.marketticker.infrastructure.naver

import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals("005930", ticker.symbol)
        assertEquals("삼성전자", ticker.name)
        assertEquals("KOSPI", ticker.marketCode)
        assertEquals("코스피", ticker.marketName)
        assertEquals("KOR", ticker.nationCode)
        assertEquals("대한민국", ticker.nationName)
    }
}