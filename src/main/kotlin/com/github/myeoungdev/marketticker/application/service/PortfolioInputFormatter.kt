package com.github.myeoungdev.marketticker.application.service

import java.math.BigDecimal

object PortfolioInputFormatter {

    fun format(value: Double?): String {
        if (value == null || !value.isFinite()) return ""
        return BigDecimal.valueOf(value)
            .stripTrailingZeros()
            .toPlainString()
    }

    fun parse(value: String): Double? {
        val normalized = value.trim().replace(",", "")
        if (normalized.isBlank()) return null
        return normalized
            .toDoubleOrNull()
            ?.takeIf { it > 0.0 && it.isFinite() }
    }
}
