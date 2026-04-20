package com.github.myeoungdev.marketticker.domain.model.screener

import com.github.myeoungdev.marketticker.domain.model.MarketType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarketTypeScreenerExtensionsTest {

    @Test
    fun `스크리너 시장 목록은 MarketType 하나로만 구성된다`() {
        assertThat(MarketType.screenerMarkets()).containsExactly(
            MarketType.KOREA,
            MarketType.USA,
            MarketType.SHANGHAI,
            MarketType.HONG_KONG,
            MarketType.TOKYO,
            MarketType.VIETNAM,
            MarketType.UPBIT,
            MarketType.BITHUMB
        )
    }

    @Test
    fun `코인 스크리너는 PRICE_TOP 을 제공하지 않는다`() {
        assertThat(MarketType.UPBIT.availableScreenerPresets()).containsExactly(
            ScreenerPreset.SEARCH_TOP,
            ScreenerPreset.MARKET_CAP,
            ScreenerPreset.UP,
            ScreenerPreset.DOWN
        )
    }
}
