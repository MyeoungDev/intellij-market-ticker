package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.research.ResearchArticle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchCategory
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingBundle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingType

enum class ResearchLoadStatus { SUCCESS, EMPTY, FAILED }

data class ResearchLoadResult<T>(
    val value: T,
    val status: ResearchLoadStatus,
    val message: String = ""
)

interface ResearchProvider {
    fun getCategoryLatestResearch(): Map<ResearchCategory, List<ResearchArticle>>

    fun getCategoryLatestResearchResult(): ResearchLoadResult<Map<ResearchCategory, List<ResearchArticle>>> =
        runCatching { getCategoryLatestResearch() }
            .fold(
                onSuccess = { value -> ResearchLoadResult(value, if (value.values.flatten().isEmpty()) ResearchLoadStatus.EMPTY else ResearchLoadStatus.SUCCESS) },
                onFailure = { error -> ResearchLoadResult(emptyMap(), ResearchLoadStatus.FAILED, error.message.orEmpty()) }
            )

    fun getResearchRanking(rankingType: ResearchRankingType, selectedRank: Int): ResearchRankingBundle

    fun getResearchRankingResult(rankingType: ResearchRankingType, selectedRank: Int): ResearchLoadResult<ResearchRankingBundle> =
        runCatching { getResearchRanking(rankingType, selectedRank) }
            .fold(
                onSuccess = { value -> ResearchLoadResult(value, if (value.ranking.isEmpty() && value.latestResearch.isEmpty()) ResearchLoadStatus.EMPTY else ResearchLoadStatus.SUCCESS) },
                onFailure = { error -> ResearchLoadResult(ResearchRankingBundle(), ResearchLoadStatus.FAILED, error.message.orEmpty()) }
            )

    fun getStockResearch(itemCode: String, size: Int = 10): List<ResearchArticle>

    fun getStockResearchResult(itemCode: String, size: Int = 10): ResearchLoadResult<List<ResearchArticle>> =
        runCatching { getStockResearch(itemCode, size) }
            .fold(
                onSuccess = { value -> ResearchLoadResult(value, if (value.isEmpty()) ResearchLoadStatus.EMPTY else ResearchLoadStatus.SUCCESS) },
                onFailure = { error -> ResearchLoadResult(emptyList(), ResearchLoadStatus.FAILED, error.message.orEmpty()) }
            )
}
