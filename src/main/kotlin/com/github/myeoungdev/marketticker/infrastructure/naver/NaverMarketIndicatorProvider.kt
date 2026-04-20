package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.MarketIndicatorProvider
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator

class NaverMarketIndicatorProvider(
    private val client: NaverClient = NaverClient()
) : MarketIndicatorProvider {

    override fun getIndicators(): List<MarketIndicator> {
        val domestic = client.fetchDomesticIndices(listOf("KOSPI", "KOSDAQ", "KPI200"))
            .datas.map { it.toMarketIndicator(IndicatorCategory.DOMESTIC_INDEX) }

        val world = client.fetchWorldIndices(listOf(".DJI", ".INX", ".IXIC"))
            .datas.map { it.toMarketIndicator(IndicatorCategory.WORLD_INDEX) }

        val metals = client.fetchMarketCommodity("metals", "GCcv1")
            .datas.map { it.toMarketIndicator(IndicatorCategory.METAL) }

        val energy = client.fetchMarketCommodity("energy", "CLcv1")
            .datas.map { it.toMarketIndicator(IndicatorCategory.ENERGY) }

        return domestic + world + metals + energy
    }
}
