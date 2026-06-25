package com.github.myeoungdev.marketticker.domain.model.news

import java.time.Instant

/**
 * 플러그인 내부에서 공통으로 사용하는 뉴스 도메인 모델입니다.
 */
data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String = "",
    val source: String = "",
    val publishedAt: String = "",
    val publishedAtInstant: Instant? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val sectionLabel: String = "",
    val badgeLabel: String = "",
    val badgeColor: String? = null,
    val ranking: String? = null
)

data class NewsSection(
    val title: String,
    val articles: List<NewsArticle>
)

data class HeadlineNewsBundle(
    val headlines: Map<String, List<NewsArticle>> = emptyMap(),
    val worldNews: List<NewsArticle> = emptyList(),
    val moneyStories: List<NewsArticle> = emptyList(),
    val focusSections: List<NewsSection> = emptyList()
)

data class TickerOverviewCard(
    val title: String,
    val metaPrimary: String,
    val metaSecondary: String,
    val primaryMetrics: String,
    val secondaryMetrics: String,
    val summary: String,
    val siteUrl: String?
)

data class TickerNewsBundle(
    val overviewCard: TickerOverviewCard?,
    val articles: List<NewsArticle>
)
