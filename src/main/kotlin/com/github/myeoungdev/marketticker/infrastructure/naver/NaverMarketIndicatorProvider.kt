package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.MarketIndicatorProvider
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator

class NaverMarketIndicatorProvider(
    private val client: NaverClient = NaverClient()
) : MarketIndicatorProvider {

    private val worldIndexCodes = listOf(".DJI", ".INX", ".IXIC", ".SOX", ".VIX")
    private val exchangeRateCodes = listOf("FX_USDKRW", "FX_JPYKRW", "FX_EURKRW", "FX_CNYKRW", "FX_HKDKRW")
    private val metalCodes = listOf(
        "GCcv1", // 국제 금
        "SIcv1", // 은
        "HGcv1", // 구리
        "PLcv1", // 백금
        "PAcv1"  // 팔라듐
    )
    private val energyCodes = listOf(
        "CLcv1", // WTI
        "NGcv1", // 천연가스
        "HOcv1", // 난방유
        "RBcv1"  // RBOB 가솔린
    )

    override fun getIndicators(): List<MarketIndicator> {
        val domestic = client.fetchDomesticIndices(listOf("KOSPI", "KOSDAQ", "KPI200"))
            .datas.map { it.toMarketIndicator(IndicatorCategory.DOMESTIC_INDEX) }

        val world = client.fetchWorldIndices(worldIndexCodes)
            .datas.map { it.toMarketIndicator(IndicatorCategory.WORLD_INDEX) }

        val exchangeRatesByCode = client.fetchExchangeRates()
            .associateBy { it.marketIndexCd }
        val exchangeRates = exchangeRateCodes.mapNotNull { code ->
            exchangeRatesByCode[code]?.toMarketIndicator()
        }

        val metals = metalCodes.flatMap { code ->
            client.fetchMarketCommodity("metals", code)
                .datas.map { it.toMarketIndicator(IndicatorCategory.METAL) }
        }

        val energy = energyCodes.flatMap { code ->
            client.fetchMarketCommodity("energy", code)
                .datas.map { it.toMarketIndicator(IndicatorCategory.ENERGY) }
        }

        return domestic + world + exchangeRates + metals + energy
    }
}
