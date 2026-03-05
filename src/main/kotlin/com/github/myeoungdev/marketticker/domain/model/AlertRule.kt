package com.github.myeoungdev.marketticker.domain.model

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-23
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
