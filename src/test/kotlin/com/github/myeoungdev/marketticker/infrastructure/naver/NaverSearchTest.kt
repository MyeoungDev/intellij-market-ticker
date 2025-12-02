package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
class NaverSearchTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `검색 응답 JSON을 DTO로 파싱할 수 있다`() {
        val json = """
            {
              "isSuccess": true,
              "detailCode": "",
              "message": "",
              "result": {
                "query": "삼성전",
                "items": [
                  {
                    "code": "005930",
                    "name": "삼성전자",
                    "typeCode": "KOSPI",
                    "typeName": "코스피",
                    "url": "/domestic/stock/005930/total",
                    "reutersCode": "005930",
                    "nationCode": "KOR",
                    "nationName": "대한민국",
                    "category": "stock"
                  }
                ]
              }
            }
        """.trimIndent()

        val wrapper: NaverResponse<NaverSearchResultPayload> =
            mapper.readValue(json)

        require(wrapper.result != null)

        assertEquals(true, wrapper.isSuccess)
        assertEquals("삼성전", wrapper.result!!.query)
        assertEquals(1, wrapper.result!!.items.size)

        val item = wrapper.result!!.items.first()
        assertEquals("005930", item.code)
        assertEquals("삼성전자", item.name)
        assertEquals("KOSPI", item.typeCode)
    }

    @Test
    fun `NaverSearchItem을 Ticker 도메인으로 매핑한다`() {
        val item = NaverSearchItem(
            code = "005930",
            name = "삼성전자",
            typeCode = "KOSPI",
            typeName = "코스피",
            url = "/domestic/stock/005930/total",
            reutersCode = "005930",
            nationCode = "KOR",
            nationName = "대한민국",
            category = "stock"
        )

        val ticker = item.toTicker()

        assertEquals("005930", ticker.symbol)
        assertEquals("삼성전자", ticker.name)
        assertEquals("KOSPI", ticker.marketCode)
        assertEquals("코스피", ticker.marketName)
        assertEquals("KOR", ticker.nationCode)
        assertEquals("대한민국", ticker.nationName)
    }

}