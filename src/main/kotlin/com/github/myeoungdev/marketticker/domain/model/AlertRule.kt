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
    var volatilityPercentage: Double = 5.0,
    var isEnabled: Boolean = true
)