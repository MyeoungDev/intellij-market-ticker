package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.AlertMode
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.github.myeoungdev.marketticker.fixtures.domain.AlertRuleFixtures
import com.github.myeoungdev.marketticker.fixtures.domain.TickerPriceFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


@DisplayName("가격 알림 서비스 테스트")
class PriceAlertServiceTest {

    private lateinit var priceAlertService: PriceAlertService

    @BeforeEach
    fun setUp() {
        priceAlertService = PriceAlertService()
    }

    @Nested
    @DisplayName("1. 변동률(Volatility) 알림 규칙 테스트")
    inner class VolatilityTest {

        @Test
        fun `설정된 변동률(5%)보다 가격이 더 많이 오르면 알림이 울려야 한다`() {
            // Given: 변동률 5% 알림 설정
            val rule = AlertRuleFixtures.VOLATILITY_DEFAULT
            priceAlertService.addAlert(rule)

            // When: 6% 상승한 가격 정보 수신
            val risingPrice = TickerPriceFixtures.create(
                symbol = rule.symbol,
                changeRate = 6.0
            )

            // Then
            val result = priceAlertService.shouldTriggerAlert(risingPrice)
            assertThat(result).isTrue()
        }

        @Test
        fun `설정된 변동률(5%)보다 가격이 더 많이 떨어져도(절대값) 알림이 울려야 한다`() {
            // Given: 변동률 5% 알림 설정
            val rule = AlertRuleFixtures.VOLATILITY_DEFAULT
            priceAlertService.addAlert(rule)

            // When: 5.5% 하락한 가격 정보 수신
            val fallingPrice = TickerPriceFixtures.TESLA_FALLING.copy(
                symbol = rule.symbol,
                changeRate = -5.5
            )

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(fallingPrice)).isTrue()
        }

        @Test
        fun `변동률이 설정값 미만이면 알림이 울리지 않아야 한다`() {
            // Given: 변동률 5% 설정
            val rule = AlertRuleFixtures.VOLATILITY_DEFAULT
            priceAlertService.addAlert(rule)

            // When: 3% 상승한 가격
            val price = TickerPriceFixtures.create(
                symbol = rule.symbol,
                changeRate = 3.0
            )

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(price)).isFalse()
        }

        @Test
        fun `변동률 알림이 꺼져있으면(disable) 변동폭이 커도 알림이 울리지 않아야 한다`() {
            // Given: 목표가 알림만 켜진 규칙 (변동률 OFF)
            val rule = AlertRuleFixtures.TARGET_PRICE_HIT.copy(
                isVolatilityEnabled = false,
                volatilityPercentage = 1.0
            )
            priceAlertService.addAlert(rule)

            // When: 10% 급등
            val price = TickerPriceFixtures.create(
                symbol = rule.symbol,
                changeRate = 10.0
            )

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(price)).isFalse()
        }
    }

    @Nested
    @DisplayName("2. 목표가(Target Price) 알림 규칙 테스트")
    inner class TargetPriceTest {

        @Test
        @DisplayName("현재가가 목표가와 정확히 일치하면 알림이 울려야 한다")
        fun `현재가가 목표가와 정확히 일치하면 알림이 울려야 한다`() {
            // Given: 목표가 $200 설정
            val rule = AlertRuleFixtures.TARGET_PRICE_HIT
            priceAlertService.addAlert(rule)

            // When: 현재가 $200
            val price = TickerPriceFixtures.create(
                symbol = rule.symbol,
                currentPrice = 200.0
            )

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(price)).isTrue()
        }

        @Test
        fun `현재가가 목표가의 오차범위 내에 진입하면 알림이 울려야 한다 (근접 알림)`() {
            // Given: 목표가 $200 (오차범위 0.5% = $1.0) -> $199 ~ $201 사이면 울림
            val rule = AlertRuleFixtures.TARGET_PRICE_HIT
            priceAlertService.addAlert(rule)

            // When: $199.5 (범위 내)
            val nearPrice = TickerPriceFixtures.create(
                symbol = rule.symbol,
                currentPrice = 199.5
            )

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(nearPrice)).isTrue()
        }

        @Test
        fun `현재가가 목표가와 멀리 떨어져 있으면 알림이 울리지 않아야 한다`() {
            // Given: 목표가 $200
            val rule = AlertRuleFixtures.TARGET_PRICE_HIT
            priceAlertService.addAlert(rule)

            // When: $180 (범위 밖)
            val farPrice = TickerPriceFixtures.create(
                symbol = rule.symbol,
                currentPrice = 180.0
            )

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(farPrice)).isFalse()
        }

        @Test
        fun `한국 주식(원화) 목표가 도달 테스트`() {
            // Given: 삼성전자 8만원 목표
            val rule = AlertRuleFixtures.SAMSUNG_TARGET_80K
            priceAlertService.addAlert(rule)

            // When: 80,100원 (근접)
            val price = TickerPriceFixtures.SAMSUNG_KRW.copy(
                currentPrice = 80100.0
            )

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(price)).isTrue()
        }
    }

    @Nested
    @DisplayName("3. 엣지 케이스 및 예외 처리")
    inner class EdgeCaseTest {

        @Test
        fun `알림 규칙 자체가 비활성화(Enabled=False)되어 있으면 어떤 조건에서도 울리지 않는다`() {
            // Given: 전체 알림 OFF 설정
            val rule = AlertRuleFixtures.DISABLED_RULE
            priceAlertService.addAlert(rule)

            // When: 엄청난 급등 발생
            val extremePrice = TickerPriceFixtures.create(
                symbol = rule.symbol,
                changeRate = 100.0,
                currentPrice = 99999.0
            )

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(extremePrice)).isFalse()
        }

        @Test
        fun `등록되지 않은 종목의 가격 정보가 들어오면 무시한다`() {
            // Given: 아무 규칙도 등록하지 않음

            // When: 임의의 가격 정보 수신
            val price = TickerPriceFixtures.APPLE_RISING

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(price)).isFalse()
        }

        @Test
        fun `목표가와 변동률 조건이 동시에 만족되면 알림이 울린다`() {
            // Given: 목표가 $500, 변동률 3%
            val rule = AlertRuleFixtures.FULL_OPTION_ALERT // NVDA
            priceAlertService.addAlert(rule)

            // When: $500 도달(목표가 만족) AND 4% 급등(변동률 만족)
            val price = TickerPriceFixtures.create(
                symbol = "NVDA",
                currentPrice = 500.0,
                changeRate = 4.0
            )

            // Then
            assertThat(priceAlertService.shouldTriggerAlert(price)).isTrue()
        }
    }

    @Nested
    @DisplayName("4. 고급 알림 옵션 테스트")
    inner class AdvancedAlertOptionTest {

        @Test
        fun `장중 전용 옵션이면 장마감에서는 알림이 울리지 않는다`() {
            val rule = AlertRuleFixtures.create(
                symbol = "AAPL",
                isVolatilityEnabled = true,
                volatilityPercentage = 1.0,
                marketHoursOnly = true
            )
            priceAlertService.addAlert(rule)

            val closedPrice = TickerPriceFixtures.APPLE_RISING.copy(
                symbol = "AAPL",
                marketStatus = MarketStatus.CLOSED,
                changeRate = 2.0
            )

            assertThat(priceAlertService.shouldTriggerAlert(closedPrice)).isFalse()
        }

        @Test
        fun `once 모드에서 이미 트리거된 알림은 재발송되지 않는다`() {
            val rule = AlertRuleFixtures.create(
                symbol = "AAPL",
                isVolatilityEnabled = true,
                volatilityPercentage = 1.0,
                alertMode = AlertMode.ONCE.name
            )
            priceAlertService.addAlert(rule)

            val price = TickerPriceFixtures.APPLE_RISING.copy(symbol = "AAPL", changeRate = 3.0)
            assertThat(priceAlertService.shouldTriggerAlert(price)).isTrue()

            priceAlertService.markTriggered("AAPL")

            assertThat(priceAlertService.shouldTriggerAlert(price)).isFalse()
        }

        @Test
        fun `symbol 조회는 대소문자를 정규화한다`() {
            val rule = AlertRuleFixtures.create(symbol = "nvda", isVolatilityEnabled = true, volatilityPercentage = 1.0)
            priceAlertService.addAlert(rule)

            val upper = priceAlertService.getAlert("NVDA")
            val lower = priceAlertService.getAlert("nvda")

            assertThat(upper).isNotNull
            assertThat(lower).isNotNull
        }
    }
}
