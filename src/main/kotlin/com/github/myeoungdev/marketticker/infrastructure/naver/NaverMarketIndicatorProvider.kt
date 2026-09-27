package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.MarketIndicatorProvider
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

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
        "RBcv1", // RBOB 가솔린
        "LCOcv1" // 브렌트 원유
    )
    private val treasurySpecs = listOf(
        TreasurySpec(TreasuryNation.USA, "US2YT=RR", "미국 국채 2년", "US Treasury 2Y"),
        TreasurySpec(TreasuryNation.USA, "US5YT=RR", "미국 국채 5년", "US Treasury 5Y"),
        TreasurySpec(TreasuryNation.USA, "US10YT=RR", "미국 국채 10년", "US Treasury 10Y"),
        TreasurySpec(TreasuryNation.USA, "US30YT=RR", "미국 국채 30년", "US Treasury 30Y"),
        TreasurySpec(TreasuryNation.KOR, "KR2YT=RR", "한국 국채 2년", "Korea Treasury 2Y"),
        TreasurySpec(TreasuryNation.KOR, "KR3YT=RR", "한국 국채 3년", "Korea Treasury 3Y"),
        TreasurySpec(TreasuryNation.KOR, "KR5YT=RR", "한국 국채 5년", "Korea Treasury 5Y"),
        TreasurySpec(TreasuryNation.KOR, "KR10YT=RR", "한국 국채 10년", "Korea Treasury 10Y"),
        TreasurySpec(TreasuryNation.KOR, "KR30YT=RR", "한국 국채 30년", "Korea Treasury 30Y")
    )

    override fun getIndicators(): List<MarketIndicator> {
        val domestic = safeGroup("domestic indices") {
            client.fetchDomesticIndices(listOf("KOSPI", "KOSDAQ", "KPI200"))
                .datas.mapSafely { it.toMarketIndicator(IndicatorCategory.DOMESTIC_INDEX) }
        }

        val world = safeGroup("world indices") {
            client.fetchWorldIndices(worldIndexCodes)
                .datas.mapSafely { it.toMarketIndicator(IndicatorCategory.WORLD_INDEX) }
        }

        val exchangeRates = safeGroup("exchange rates") {
            val exchangeRatesByCode = client.fetchExchangeRates().mapNotNull { item ->
                item.normalizedMarketIndexCode()?.let { it to item }
            }.toMap()
            exchangeRateCodes.mapNotNull { code ->
                exchangeRatesByCode[code]?.let { item ->
                    runCatching { item.toMarketIndicator() }.onFailure { error ->
                        logger.warn(error) { "Failed to map exchange rate indicator: $code" }
                    }.getOrNull()
                }
            }
        }

        val metals = safeGroup("metals") {
            metalCodes.flatMap { code ->
                client.fetchMarketCommodity("metals", code).datas
                    .mapSafely { it.toMarketIndicator(IndicatorCategory.METAL) }
            }
        }

        val energy = safeGroup("energy") {
            energyCodes.flatMap { code ->
                client.fetchMarketCommodity("energy", code).datas
                    .mapSafely { it.toMarketIndicator(IndicatorCategory.ENERGY) }
            }
        }

        val bonds = treasurySpecs.groupBy { it.nation }.flatMap { (nation, specs) ->
            safeGroup("treasury bonds ${nation.pathValue}") {
                val bondsByCode = client.fetchTreasuryBonds(nation)
                    .associateBy { it.reutersCode ?: it.symbolCode }
                specs.mapNotNull { spec ->
                    bondsByCode[spec.code]?.let { item ->
                        runCatching {
                            item.toMarketIndicator(IndicatorCategory.BOND).copy(
                                name = spec.displayName,
                                englishName = spec.englishName,
                                unit = "%"
                            )
                        }.onFailure { error ->
                            logger.warn(error) { "Failed to map treasury indicator: ${spec.code}" }
                        }.getOrNull()
                    }
                }
            }
        }

        return domestic + world + exchangeRates + metals + energy + bonds
    }

    private inline fun <T, R> Iterable<T>.mapSafely(transform: (T) -> R): List<R> = mapNotNull {
        runCatching { transform(it) }.onFailure { error ->
            logger.warn(error) { "Failed to map market indicator item" }
        }.getOrNull()
    }

    private inline fun <T> safeGroup(name: String, block: () -> List<T>): List<T> {
        return runCatching { block() }.onFailure { error ->
            logger.warn(error) { "Failed to fetch market indicator group: $name" }
        }.getOrDefault(emptyList())
    }

    private data class TreasurySpec(
        val nation: TreasuryNation,
        val code: String,
        val displayName: String,
        val englishName: String
    )
}
