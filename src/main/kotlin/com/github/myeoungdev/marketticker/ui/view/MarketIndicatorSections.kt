package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator

internal data class MarketIndicatorSection(
    val category: IndicatorCategory,
    val indicators: List<MarketIndicator>
)

internal fun groupMarketIndicators(indicators: List<MarketIndicator>): List<MarketIndicatorSection> {
    val orderedCategories = listOf(
        IndicatorCategory.EXCHANGE_RATE,
        IndicatorCategory.DOMESTIC_INDEX,
        IndicatorCategory.WORLD_INDEX,
        IndicatorCategory.METAL,
        IndicatorCategory.ENERGY
    )

    return orderedCategories.mapNotNull { category ->
        val items = indicators.filter { it.category == category }
        if (items.isEmpty()) {
            null
        } else {
            MarketIndicatorSection(category = category, indicators = items)
        }
    }
}
