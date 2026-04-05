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

        return client.fetchResearchRanking(
            rankingType = rankingType.toNaverRankingType(),
            selectedRank = 1
        ).ranking.take(limit).map { item ->
            val marketType = when (item.sosok) {
                "0" -> MarketType.KOSPI
                "1" -> MarketType.KOSDAQ
                else -> MarketType.UNKNOWN
            }
            ScreenedTicker(
                ticker = Ticker(
                    symbol = item.itemCode,
                    tradingSymbol = item.itemCode,
                    name = item.itemName,
                    marketType = marketType,
                    nationCode = "KOR",
                    nationName = "대한민국"
                ),
                sector = marketType.displayName,
                industry = preset.labelKo,
                exchange = item.marketStatus,
                marketCap = item.marketSum,
                pe = item.per,
                price = item.nowVal,
                change = item.changeRate,
                volume = item.tradeVolume,
                signalLabel = preset.labelEn
            )
        }
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
