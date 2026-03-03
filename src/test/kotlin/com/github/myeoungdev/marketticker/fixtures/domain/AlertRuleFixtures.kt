package com.github.myeoungdev.marketticker.fixtures.domain

import com.github.myeoungdev.marketticker.domain.model.AlertRule
import com.github.myeoungdev.marketticker.domain.model.AlertMode

/**
 * Some Descirption...
 *
 * @author : 강명관
 * @since : 1.0
 **/
object AlertRuleFixtures {

    /**
     * AlertRule 기본 Factory 메서드
     */
    fun create(
        symbol: String = "AAPL",
        tradingSymbol: String = "AAPL.O",
        targetPrice: Double? = null,
        isTargetPriceEnabled: Boolean = false,
        volatilityPercentage: Double = 5.0,
        isVolatilityEnabled: Boolean = true,
        isEnabled: Boolean = true,
        alertMode: String = AlertMode.REPEATING.name,
        repeatIntervalMinutes: Int = 5,
        marketHoursOnly: Boolean = false,
        soundEnabled: Boolean = false
    ): AlertRule {
        return AlertRule(
            symbol = symbol,
            tradingSymbol = tradingSymbol,
            targetPrice = targetPrice,
            isTargetPriceEnabled = isTargetPriceEnabled,
            volatilityPercentage = volatilityPercentage,
            isVolatilityEnabled = isVolatilityEnabled,
            isEnabled = isEnabled,
            alertMode = alertMode,
            repeatIntervalMinutes = repeatIntervalMinutes,
            marketHoursOnly = marketHoursOnly,
            soundEnabled = soundEnabled
        )
    }


    /**
     * 변동성 - 기본 급등락 알림 (5% 이상 변동 시)
     */
    val VOLATILITY_DEFAULT = create(
        symbol = "AAPL",
        isTargetPriceEnabled = false,
        volatilityPercentage = 5.0,
        isVolatilityEnabled = true
    )

    /**
     * 목표가 - 특정 가격 도달 알림 (테슬라 200$ 돌파 알림)
     */
    val TARGET_PRICE_HIT = create(
        symbol = "TSLA",
        tradingSymbol = "TSLA.O",
        targetPrice = 200.0,
        isTargetPriceEnabled = true,
        isVolatilityEnabled = false
    )

    /**
     * 변동성 + 목표가 - 변동률과 목표가 도달 둘 다 사용.
     */
    val FULL_OPTION_ALERT = create(
        symbol = "NVDA",
        targetPrice = 500.0,
        isTargetPriceEnabled = true,
        volatilityPercentage = 3.0, // 3%만 움직여도 알림
        isVolatilityEnabled = true
    )

    /**
     * 비활성화 - 설정은 했지만 알림 기능 사용하지 않음.
     */
    val DISABLED_RULE = create(
        symbol = "MSFT",
        isEnabled = false // 전체 OFF
    )

    /**
     * 매우 낮은 변동성 - 매우 낮은 변동성 퍼센트 설정
     */
    val SENSITIVE_SCALPER = create(
        symbol = "COIN",
        volatilityPercentage = 1.0,
        isVolatilityEnabled = true
    )

    /**
     * 목표가 - 한국 삼성전자
     */
    val SAMSUNG_TARGET_80K = create(
        symbol = "005930",
        tradingSymbol = "005930",
        targetPrice = 80000.0,
        isTargetPriceEnabled = true
    )
}
