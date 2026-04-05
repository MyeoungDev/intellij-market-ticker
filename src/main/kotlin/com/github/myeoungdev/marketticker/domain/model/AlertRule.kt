package com.github.myeoungdev.marketticker.domain.model

/**
 * 사용자 정의 가격 알림 규칙입니다.
 */
data class AlertRule(
    var symbol: String = "",
    var tradingSymbol: String = "",
    var targetPrice: Double? = null,
    var isTargetPriceEnabled: Boolean = false,
    var volatilityPercentage: Double = 5.0,
    var isVolatilityEnabled: Boolean = true,
    var isEnabled: Boolean = true,
    var alertMode: String = AlertMode.REPEATING.name,
    var repeatIntervalMinutes: Int = 5,
    var marketHoursOnly: Boolean = false,
    var soundEnabled: Boolean = false,
    var triggeredOnce: Boolean = false
)
