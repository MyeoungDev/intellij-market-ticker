package com.github.myeoungdev.marketticker.application.model.news

import com.github.myeoungdev.marketticker.domain.model.news.HeadlineNewsBundle
import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.github.myeoungdev.marketticker.domain.model.news.TickerOverviewCard

data class NewsHomeViewData(
    val headlines: HeadlineNewsBundle,
    val mostViewed: List<NewsArticle>
)

data class TickerNewsSummaryViewData(
    val title: String,
    val subtitle: String,
    val overviewCard: TickerOverviewCard?,
    val articles: List<NewsArticle>,
    val statusMessage: String
)
