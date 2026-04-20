package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.MarketIndicator

/**
 * 시장 지표 데이터를 공급하는 provider 인터페이스입니다.
 */
interface MarketIndicatorProvider {
    fun getIndicators(): List<MarketIndicator>
}
