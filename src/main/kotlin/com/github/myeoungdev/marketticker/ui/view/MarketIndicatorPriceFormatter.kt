package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.CurrencyType

internal fun formatIndicatorPrice(
    value: Double,
    unit: String?,
    formatDecimal: (Double, Int) -> String,
    formatAmount: (Double, CurrencyType?, Int) -> String
): String {
    val normalizedUnit = unit?.trim().orEmpty()
    return if (normalizedUnit.contains('/')) {
        "${formatDecimal(value, 2)} $normalizedUnit"
    } else {
        formatAmount(value, CurrencyType.of(normalizedUnit), 2)
    }
}
