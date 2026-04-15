package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.ScreenerProvider
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenedTicker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenerPreset
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingType

class NaverScreenerProvider(
    private val client: NaverClient = NaverClient()
) : ScreenerProvider {

    override fun getScreen(preset: ScreenerPreset, limit: Int): List<ScreenedTicker> {
        val rankingType = when (preset) {
            ScreenerPreset.SEARCH_TOP -> ResearchRankingType.SEARCH_TOP
            ScreenerPreset.PRICE_TOP -> ResearchRankingType.PRICE_TOP
            ScreenerPreset.UP -> ResearchRankingType.UP
            ScreenerPreset.DOWN -> ResearchRankingType.DOWN
        }

        val rows = mutableListOf<ScreenedTicker>()
        val seenSymbols = linkedSetOf<String>()

        for (selectedRank in 1..10) {
            val batch = client.fetchResearchRanking(
                rankingType = rankingType.toNaverRankingType(),
                selectedRank = selectedRank
            ).ranking
            if (batch.isEmpty()) {
                break
            }
            batch.forEach { item ->
                if (!seenSymbols.add(item.itemCode)) return@forEach
                rows += item.toScreenedTicker(preset)
                if (rows.size >= limit) {
                    return rows
                }
            }
            if (batch.size < 5) {
                break
            }
        }

        return rows
    }

    private fun com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchRankingItem.toScreenedTicker(
        preset: ScreenerPreset
    ): ScreenedTicker {
        val marketType = when (sosok) {
            "0" -> MarketType.KOSPI
            "1" -> MarketType.KOSDAQ
            else -> MarketType.UNKNOWN
        }
        return ScreenedTicker(
            ticker = Ticker(
                symbol = itemCode,
                tradingSymbol = itemCode,
                name = itemName,
                marketType = marketType,
                nationCode = "KOR",
                nationName = "대한민국"
            ),
            sector = marketType.displayName,
            industry = preset.labelKo,
            exchange = marketStatus,
            marketCap = marketSum.orEmpty(),
            pe = per.orEmpty(),
            price = nowVal.orEmpty(),
            change = changeRate.orEmpty(),
            volume = tradeVolume.orEmpty(),
            signalLabel = preset.labelEn
        )
    }

    private fun ResearchRankingType.toNaverRankingType(): com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchRankingType {
        return when (this) {
            ResearchRankingType.SEARCH_TOP -> com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchRankingType.SEARCH_TOP
            ResearchRankingType.PRICE_TOP -> com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchRankingType.PRICE_TOP
            ResearchRankingType.UP -> com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchRankingType.UP
            ResearchRankingType.DOWN -> com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchRankingType.DOWN
        }
    }
}
