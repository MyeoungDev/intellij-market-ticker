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

    @Test
    @DisplayName("전일 대비 하락했을 때 전일 종가가 올바르게 계산되는지 검증")
    fun `하락장 전일종가 계산 테스트`() {
        // given: 현재가 10,000원, 전일대비 500원 하락 (네이버는 부호를 안 줄 수도 있음)
        // API 응답에서 하락 코드("5")나 부호가 어떻게 오는지에 따라
        // 로직이 달라지므로 이 테스트가 필수입니다.
        val stock = NaverFixtures.createNaverStockPrice(
            closePrice = "10,000",
            compareToPreviousClosePrice = "500",
            // 만약 하락을 나타내는 필드(CompareStatus 등)가 있다면 그것도 설정
        )

        // when
        val result = stock.toTickerPrice()

        // then
        // 하락이면: 전일종가(10,500) - 500 = 현재가(10,000) 이어야 함.
        // 현재 로직(단순 뺄셈)이면: 10,000 - 500 = 9,500이 되어버림 (버그 발견 가능성!)
        assertThat(result.previousClosePrice).isEqualTo(10500.0)
    }
}