package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.AlertMode
import com.github.myeoungdev.marketticker.fixtures.domain.AlertRuleFixtures
import com.github.myeoungdev.marketticker.fixtures.domain.TickerPriceFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationRepeatGateTest {

    @Test
    fun `임계값 아래로 내려갔다 다시 넘어도 반복 간격 전에는 재발송하지 않는다`() {
        var now = 1_000_000L
        val gate = NotificationRepeatGate { now }
        val alertService = PriceAlertService()
        val rule = AlertRuleFixtures.VOLATILITY_DEFAULT.copy(volatilityPercentage = 5.0)
        alertService.addAlert(rule)
        val repeatIntervalMinutes = 5
        val allowedAlerts = mutableListOf<Double>()

        listOf(5.0, 4.9, 5.0).forEach { changeRate ->
            val price = TickerPriceFixtures.APPLE_RISING.copy(symbol = rule.symbol, changeRate = changeRate)
            if (alertService.shouldTriggerAlert(price) &&
                gate.allow(rule.symbol, AlertMode.REPEATING, repeatIntervalMinutes)
            ) {
                allowedAlerts += changeRate
            }
            now += 1_000L
        }

        assertThat(allowedAlerts).containsExactly(5.0)

        now += repeatIntervalMinutes * 60_000L
        val afterInterval = TickerPriceFixtures.APPLE_RISING.copy(symbol = rule.symbol, changeRate = 5.0)
        if (alertService.shouldTriggerAlert(afterInterval) &&
            gate.allow(rule.symbol, AlertMode.REPEATING, repeatIntervalMinutes)
        ) {
            allowedAlerts += 5.0
        }

        assertThat(allowedAlerts).containsExactly(5.0, 5.0)
    }

    @Test
    fun `상승과 하락은 절댓값 기준으로 임계값을 만족한다`() {
        val alertService = PriceAlertService()
        val rule = AlertRuleFixtures.VOLATILITY_DEFAULT.copy(volatilityPercentage = 5.0)
        alertService.addAlert(rule)

        assertThat(alertService.shouldTriggerAlert(
            TickerPriceFixtures.APPLE_RISING.copy(symbol = rule.symbol, changeRate = 5.0)
        )).isTrue()
        assertThat(alertService.shouldTriggerAlert(
            TickerPriceFixtures.APPLE_RISING.copy(symbol = rule.symbol, changeRate = -5.0)
        )).isTrue()
        assertThat(alertService.shouldTriggerAlert(
            TickerPriceFixtures.APPLE_RISING.copy(symbol = rule.symbol, changeRate = 4.9)
        )).isFalse()
    }
}
