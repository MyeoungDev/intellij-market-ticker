package com.github.myeoungdev.marketticker.application.model.research

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.research.ResearchArticle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchCategory
import com.github.myeoungdev.marketticker.application.provider.ResearchLoadStatus

data class ResearchHomeViewData(
    val latestByCategory: Map<ResearchCategory, List<ResearchArticle>>,
    val rankingArticles: List<ResearchArticle>,
    val latestStatus: ResearchLoadStatus = ResearchLoadStatus.SUCCESS,
    val rankingStatus: ResearchLoadStatus = ResearchLoadStatus.SUCCESS,
    val latestMessage: String = "",
    val rankingMessage: String = ""
)

data class StockResearchViewData(
    val resolvedTicker: Ticker?,
    val articles: List<ResearchArticle>,
    val statusMessage: String,
    val loadStatus: ResearchLoadStatus = ResearchLoadStatus.SUCCESS,
    val errorMessage: String = ""
)

data class ResearchSummaryViewData(
    val title: String,
    val statusMessage: String,
    val articles: List<ResearchArticle>,
    val loadStatus: ResearchLoadStatus = ResearchLoadStatus.SUCCESS,
    val errorMessage: String = ""
)
