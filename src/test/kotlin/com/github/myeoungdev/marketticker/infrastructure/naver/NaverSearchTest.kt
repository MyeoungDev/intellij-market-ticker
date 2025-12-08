package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.fixtures.naver.NaverFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.jupiter.api.DisplayName

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
class NaverSearchTest {

    private val mapper = jacksonObjectMapper()

    @Test
    @DisplayName("JSON 직렬화/역직렬화가 정상적으로 동작한다")
    fun `JSON 파싱 테스트`() {
        // Given
        val originalItem = NaverFixtures.createNaverSearchItem(
            code = "035420",
            name = "NAVER"
        )
        val payload = NaverFixtures.createSearchResultPayload(items = listOf(originalItem))
        val response = NaverFixtures.createNaverResponse(result = payload)

        // When
        val jsonString = objectMapper.writeValueAsString(response)
        val parsedResponse: NaverResponse<NaverSearchResultPayload> = objectMapper.readValue(jsonString)

        // Then
        val result = parsedResponse.result!!
        assertEquals(1, result.items.size)
        assertEquals("NAVER", result.items[0].name)
        assertEquals("035420", result.items[0].code)
    }

    @Test
    @DisplayName("NaverSearchItem을 Ticker 도메인으로 매핑한다")
    fun `코스피 주식 도메인 매핑 테스트`() {
        // Given
        val item = NaverFixtures.createNaverSearchItem(
            code = "005930",
            name = "삼성전자",
            typeCode = "KOSPI",
            nationCode = "KOR"
        )

        // When
        val ticker = item.toTicker()

        // Then
        assertEquals("005930", ticker.symbol)
        assertEquals("삼성전자", ticker.name)
        assertEquals("KOSPI", ticker.marketType.code)
        assertEquals("KOR", ticker.nationCode)
        assertEquals("대한민국", ticker.nationName)
    }

    @Test
    @DisplayName("해외 주식(나스닥)도 올바른 마켓 타입으로 매핑된다")
    fun `해외주식 매핑 테스트`() {
        // Given
        val item = NaverFixtures.createNaverSearchItem(
            code = "AAPL",
            name = "Apple Inc",
            typeCode = "NASDAQ",
            nationCode = "USA"
        )

        // When
        val ticker = item.toTicker()

        // Then
        assertEquals("AAPL", ticker.symbol)
        assertEquals(MarketType.NASDAQ, ticker.marketType)
    }

}