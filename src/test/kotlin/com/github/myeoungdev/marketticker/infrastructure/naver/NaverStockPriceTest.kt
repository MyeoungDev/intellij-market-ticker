package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.fixtures.naver.NaverFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-04
 */
class NaverStockPriceTest {

    @Test
    @DisplayName("기본적인 네이버 주식 정보를 도메인 객체로 변환한다")
    fun `기본 변환 테스트`() {
        // given: 팩토리 사용 (기본값 삼성전자)
        val naverStock = NaverFixtures.createNaverStockPrice()

        // when
        val result = naverStock.toTickerPrice()

        // then
        assertThat(result.symbol).isEqualTo("005930")
        assertThat(result.currentPrice).isEqualTo(70000.0)
        assertThat(result.marketType).isEqualTo(MarketType.KOSPI)
    }

    @Test
    @DisplayName("거래소 코드가 NS(나스닥)일 경우 MarketType.NASDAQ으로 매핑된다")
    fun `나스닥 변환 테스트`() {
        // given: 팩토리의 중첩 객체(ExchangeType)만 커스텀
        val nasdaqExchange = NaverFixtures.createStockExchangeType(
            code = "NS",
            nameKor = "나스닥"
        )

        val naverStock = NaverFixtures.createNaverStockPrice(
            itemCode = "AAPL",
            stockName = "애플",
            stockExchangeType = nasdaqExchange // 갈아끼우기
        )

        // when
        val result = naverStock.toTickerPrice()

        // then
        assertThat(result.symbol).isEqualTo("AAPL")
        assertThat(result.marketType).isEqualTo(MarketType.NASDAQ)
    }

}