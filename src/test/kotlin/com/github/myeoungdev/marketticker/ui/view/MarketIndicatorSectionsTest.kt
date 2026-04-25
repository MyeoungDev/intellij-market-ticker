package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarketIndicatorSectionsTest {

    @Test
    fun `지표는 환율부터 주요 지표 순서로 섹션화한다`() {
        val sections = groupMarketIndicators(
            listOf(
                indicator("GC", IndicatorCategory.METAL),
                indicator("USD", IndicatorCategory.EXCHANGE_RATE),
                indicator("KOSPI", IndicatorCategory.DOMESTIC_INDEX),
                indicator("CL", IndicatorCategory.ENERGY)
            )
        )

        assertThat(sections.map { it.category }).containsExactly(
            IndicatorCategory.EXCHANGE_RATE,
            IndicatorCategory.DOMESTIC_INDEX,
            IndicatorCategory.METAL,
            IndicatorCategory.ENERGY
        )
    }

    @Test
    fun `비어 있는 카테고리는 제외한다`() {
        val sections = groupMarketIndicators(
            listOf(
                indicator("USD", IndicatorCategory.EXCHANGE_RATE)
            )
        )

        assertThat(sections).hasSize(1)
        assertThat(sections.single().category).isEqualTo(IndicatorCategory.EXCHANGE_RATE)
        assertThat(sections.single().indicators).hasSize(1)
    }

    @Test
    fun `카드 그리드 열 수는 폭에 맞춰 조정된다`() {
        assertThat(calculateIndicatorCardColumns(0, 3)).isEqualTo(1)
        assertThat(calculateIndicatorCardColumns(320, 3)).isEqualTo(1)
        assertThat(calculateIndicatorCardColumns(520, 3)).isEqualTo(3)
        assertThat(calculateIndicatorCardColumns(920, 5)).isEqualTo(5)
    }

    private fun indicator(code: String, category: IndicatorCategory): MarketIndicator {
        return MarketIndicator(
            code = code,
            name = code,
            currentPrice = 1.0,
            changeRate = 0.0,
            marketStatus = MarketStatus.OPEN,
            category = category,
            unit = null
        )
    }
}
