package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.ResearchProvider
import com.github.myeoungdev.marketticker.domain.model.research.ResearchArticle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchCategory
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingBundle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingItem
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingType
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchArticle
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchLatestResponse

class NaverResearchProvider(
    private val client: NaverClient = NaverClient()
) : ResearchProvider {

    override fun getCategoryLatestResearch(): Map<ResearchCategory, List<ResearchArticle>> {
        return client.fetchCategoryLatestResearch().toDomainMap()
    }

    override fun getResearchRanking(rankingType: ResearchRankingType, selectedRank: Int): ResearchRankingBundle {
        val response = client.fetchResearchRanking(
            rankingType = rankingType.toNaverRankingType(),
            selectedRank = selectedRank
        )
        return ResearchRankingBundle(
            ranking = response.ranking.map {
                ResearchRankingItem(
                    itemName = it.itemName,
                    itemCode = it.itemCode,
                    marketStatus = it.marketStatus,
                    nowVal = it.nowVal.orEmpty(),
                    changeRate = it.changeRate.orEmpty(),
                    per = it.per.orEmpty(),
                    pbr = it.pbr.orEmpty(),
                    dividendRate = it.dividendRate.orEmpty(),
                    marketSum = it.marketSum.orEmpty()
                )
            },
            latestResearch = response.latestResearch.map { it.toDomain() }
        )
    }

    override fun getStockResearch(itemCode: String, size: Int): List<ResearchArticle> {
        return client.fetchStockResearch(itemCode = itemCode, size = size).map { it.toDomain() }
    }

    private fun NaverResearchLatestResponse.toDomainMap(): Map<ResearchCategory, List<ResearchArticle>> {
        return linkedMapOf(
            ResearchCategory.MARKET to market.map { it.toDomain() },
            ResearchCategory.COMPANY to company.map { it.toDomain() },
            ResearchCategory.INDUSTRY to industry.map { it.toDomain() },
            ResearchCategory.INVEST to invest.map { it.toDomain() },
            ResearchCategory.ECONOMY to economy.map { it.toDomain() },
            ResearchCategory.DEBENTURE to debenture.map { it.toDomain() }
        )
    }

    private fun NaverResearchArticle.toDomain(): ResearchArticle {
        return ResearchArticle(
            researchCategory = researchCategory,
            category = category,
            itemCode = itemCode,
            itemName = itemName,
            researchId = researchId,
            title = title,
            content = content,
            brokerName = brokerName,
            brokerCode = brokerCode,
            writeDate = writeDate,
            readCount = readCount,
            endUrl = endUrl,
            opinion = opinion,
            goalPrice = goalPrice,
            prevGoalPrice = prevGoalPrice,
            analyst = analyst
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
