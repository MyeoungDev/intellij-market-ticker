package com.github.myeoungdev.marketticker.domain.model

import com.github.myeoungdev.marketticker.domain.model.PriceStatus.*

/**
 * 장 상태를 표현하는 도메인 모델입니다.
 */
enum class MarketStatus {
    OPEN,     // 장중 (정규장)
    CLOSED,   // 장 마감
    EXTENDED, // 시간외 (프리장/애프터장) - 미국 주식용
    HALTED,   // 거래 정지 (VI 등)
    UNKNOWN;  // 알 수 없음

    companion object {
        fun of(status: String): MarketStatus {
            return values().find { it.name == status } ?: UNKNOWN
        }
    }
}
