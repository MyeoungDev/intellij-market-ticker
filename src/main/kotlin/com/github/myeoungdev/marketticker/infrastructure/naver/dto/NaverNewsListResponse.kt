package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Naver 국내 뉴스 리스트 응답입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsListResponse(
    val articles: List<NaverNewsArticle> = emptyList(),
    val date: String? = null,
    val isFirstDate: Boolean = false
)

/**
 * Naver 국내 뉴스 단일 기사입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsArticle(
    val officeId: String? = null,
    val officeHname: String? = null,
    val articleId: String? = null,
    val title: String = "",
    val url: String? = null,
    val datetime: String? = null,
    val type: String? = null,
    val subcontent: String? = null,
    val ranking: String? = null,
    val prevRanking: String? = null,
    val sumCount: String? = null,
    val thumbUrl: String? = null
) {
    fun articleUrl(): String? {
        if (!url.isNullOrBlank()) return url
        if (officeId.isNullOrBlank() || articleId.isNullOrBlank()) return null
        return "https://n.news.naver.com/article/$officeId/$articleId"
    }
}

/**
 * Naver 뉴스 홈 집계 응답(랭킹 뉴스 추출용)입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsAggregateResponse(
    val rankingNews: List<NaverNewsRankingArticle> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsRankingArticle(
    val title: String = "",
    val url: String? = null,
    val rank: String? = null,
    val press: String? = null,
    val time: String? = null
) {
    fun toNewsArticle(): NaverNewsArticle {
        return NaverNewsArticle(
            title = title,
            url = url,
            datetime = time,
            officeHname = press,
            subcontent = null
        )
    }
}
