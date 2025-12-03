package com.github.myeoungdev.marketticker.domain.model

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-03
 */
enum class PriceStatus {
    RISING,   // 상승 (빨강)
    FALLING,  // 하락 (파랑)
    STEADY;   // 보합 (검정/회색)

    companion object {
        fun from(changeAmount: Double): PriceStatus = when {
            changeAmount > 0.0 -> RISING
            changeAmount < 0.0 -> FALLING
            else -> STEADY
        }
    }
}