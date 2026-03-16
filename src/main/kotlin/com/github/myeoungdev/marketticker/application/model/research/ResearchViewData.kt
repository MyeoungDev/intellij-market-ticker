package com.github.myeoungdev.marketticker.application.model.research

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.research.ResearchArticle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchCategory

data class ResearchHomeViewData(
    val latestByCategory: Map<ResearchCategory, List<ResearchArticle>>,
    val rankingArticles: List<ResearchArticle>
)

data class StockResearchViewData(
    val resolvedTicker: Ticker?,
    val articles: List<ResearchArticle>,
    val statusMessage: String
)

data class ResearchSummaryViewData(
    val title: String,
    val statusMessage: String,
    val articles: List<ResearchArticle>
)
