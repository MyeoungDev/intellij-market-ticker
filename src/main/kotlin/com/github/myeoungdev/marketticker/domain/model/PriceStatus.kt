package com.github.myeoungdev.marketticker.domain.model

/**
 * 가격 변동 방향을 표현하는 도메인 모델입니다.
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
