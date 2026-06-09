package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.DomesticTradeType
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenedTicker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenerPreset

/**
 * 시장 스크리너 데이터를 공급하는 provider 인터페이스입니다.
 */
interface ScreenerProvider {

    fun getScreen(
        market: MarketType,
        preset: ScreenerPreset,
        limit: Int = 25,
        domesticTradeType: DomesticTradeType = DomesticTradeType.KRX
    ): List<ScreenedTicker>
}
