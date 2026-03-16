package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.research.ResearchArticle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchCategory
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingBundle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingType

interface ResearchProvider {
    fun getCategoryLatestResearch(): Map<ResearchCategory, List<ResearchArticle>>

    fun getResearchRanking(rankingType: ResearchRankingType, selectedRank: Int): ResearchRankingBundle

    fun getStockResearch(itemCode: String, size: Int = 10): List<ResearchArticle>
}
