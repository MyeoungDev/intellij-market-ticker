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
 * 뉴스 탭에서 공통으로 사용하는 기사 모델입니다.
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
    val thumbUrl: String? = null,
    val badgeLabel: String? = null,
    val badgeColor: String? = null,
    val sectionKey: String? = null,
    val sectionLabel: String? = null,
    val isNotice: Boolean = false,
    val isOverseas: Boolean = false
) {

    /**
     * 기사 원문 URL 을 계산합니다.
     */
    fun articleUrl(): String? {
        if (!url.isNullOrBlank()) {
            return if (url.startsWith("/")) "https://stock.naver.com$url" else url
        }
        if (officeId.isNullOrBlank() || articleId.isNullOrBlank()) return null
        return "https://n.news.naver.com/article/$officeId/$articleId"
    }
}

/**
 * 해외 뉴스 리스트 응답 기사입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverWorldNewsArticle(
    val oid: String? = null,
    val ohnm: String? = null,
    val aid: String? = null,
    val tit: String = "",
    val dt: String? = null,
    val updatedt: String? = null,
    val editor: String? = null,
    val copyright: String? = null,
    val type: String? = null,
    val subcontent: String? = null,
    val thumbUrl: String? = null
) {

    /**
     * 공통 기사 모델로 변환합니다.
     */
    fun toNewsArticle(
        badgeLabel: String = "해외뉴스",
        sectionKey: String = "WORLDNEWS",
        sectionLabel: String = "해외뉴스"
    ): NaverNewsArticle {
        return NaverNewsArticle(
            officeId = oid,
            officeHname = ohnm,
            articleId = aid,
            title = tit,
            url = aid?.let { "https://stock.naver.com/news/worldnews/$it" },
            datetime = dt,
            type = type,
            subcontent = subcontent,
            thumbUrl = thumbUrl,
            badgeLabel = badgeLabel,
            badgeColor = "gray",
            sectionKey = sectionKey,
            sectionLabel = sectionLabel,
            isOverseas = true
        )
    }
}

/**
 * 종목 상세 뉴스 응답입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDomesticDetailNewsResponse(
    val total: String? = null,
    val clusters: List<NaverDomesticDetailNewsCluster> = emptyList()
)

/**
 * 종목 상세 뉴스 클러스터입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDomesticDetailNewsCluster(
    val itemTotal: String? = null,
    val items: List<NaverDomesticDetailNewsItem> = emptyList()
)

/**
 * 종목 상세 뉴스 기사입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDomesticDetailNewsItem(
    val id: String? = null,
    val officeId: String? = null,
    val articleId: String? = null,
    val officeName: String? = null,
    val datetime: String? = null,
    val type: String? = null,
    val title: String = "",
    val body: String? = null,
    val photoType: String? = null,
    val imageOriginLink: String? = null
) {

    /**
     * 공통 기사 모델로 변환합니다.
     */
    fun toNewsArticle(): NaverNewsArticle {
        return NaverNewsArticle(
            officeId = officeId,
            officeHname = officeName,
            articleId = articleId,
            title = title,
            datetime = datetime,
            type = type,
            subcontent = body,
            thumbUrl = imageOriginLink,
            badgeLabel = "종목뉴스",
            badgeColor = "blue",
            sectionKey = "ITEM_NEWS",
            sectionLabel = "종목뉴스"
        )
    }
}

/**
 * 뉴스 홈 집계 응답입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsAggregateResponse(
    val flashNews: List<NaverNewsHeadlineArticle> = emptyList(),
    val mainNews: List<NaverNewsHeadlineArticle> = emptyList(),
    val rankingNews: List<NaverNewsRankingArticle> = emptyList(),
    val overseasNews: List<NaverOverseasNewsArticle> = emptyList(),
    val newsFocus: List<NaverNewsFocusSection> = emptyList(),
    val moneyStory: List<NaverMoneyStoryArticle> = emptyList(),
    val newsNotice: NaverExchangeNoticeSection = NaverExchangeNoticeSection()
)

/**
 * 뉴스 홈의 헤드라인 기사입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsHeadlineArticle(
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val title: String = "",
    val leadtext: String? = null,
    val press: String? = null,
    val time: String? = null,
    val isVideo: Boolean = false,
    val isOverseas: Boolean = false
) {

    /**
     * 공통 기사 모델로 변환합니다.
     */
    fun toNewsArticle(badgeLabel: String? = null, badgeColor: String? = null): NaverNewsArticle {
        return NaverNewsArticle(
            title = title,
            url = url,
            datetime = time,
            officeHname = press,
            subcontent = leadtext,
            thumbUrl = thumbnailUrl,
            badgeLabel = badgeLabel,
            badgeColor = badgeColor,
            sectionKey = badgeLabel?.uppercase(),
            sectionLabel = badgeLabel,
            isOverseas = isOverseas
        )
    }
}

/**
 * 뉴스 홈의 랭킹 기사입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsRankingArticle(
    val title: String = "",
    val url: String? = null,
    val rank: String? = null,
    val press: String? = null,
    val time: String? = null,
    val rankDiff: Int? = null,
    val isOverseas: Boolean = false,
    val isNew: Boolean = false
) {

    /**
     * 공통 기사 모델로 변환합니다.
     */
    fun toNewsArticle(): NaverNewsArticle {
        return NaverNewsArticle(
            title = title,
            url = url,
            datetime = time,
            officeHname = press,
            ranking = rank,
            badgeLabel = rank?.let { "#$it" },
            badgeColor = "blue",
            sectionKey = "RANKING",
            sectionLabel = "Ranking",
            isOverseas = isOverseas
        )
    }
}

/**
 * 뉴스 홈의 해외 뉴스 기사입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverOverseasNewsArticle(
    val url: String? = null,
    val title: String = "",
    val officeHname: String? = null,
    val datetime: String? = null,
    val aid: String? = null,
    val subcontent: String? = null,
    val isVideo: Boolean = false
) {

    /**
     * 공통 기사 모델로 변환합니다.
     */
    fun toNewsArticle(): NaverNewsArticle {
        return NaverNewsArticle(
            title = title,
            url = url,
            datetime = datetime,
            officeHname = officeHname,
            subcontent = subcontent,
            badgeLabel = "GLOBAL",
            badgeColor = "gray",
            sectionKey = "GLOBAL",
            sectionLabel = "Global",
            isOverseas = true
        )
    }
}

/**
 * 뉴스 포커스 섹션입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsFocusSection(
    val categoryUrl: String? = null,
    val category: String = "",
    val news: List<NaverNewsFocusArticle> = emptyList()
)

/**
 * 포커스 섹션의 개별 기사입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsFocusArticle(
    val title: String = "",
    val press: String? = null,
    val time: String? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val isVideo: Boolean = false
) {

    /**
     * 공통 기사 모델로 변환합니다.
     */
    fun toNewsArticle(category: String): NaverNewsArticle {
        return NaverNewsArticle(
            title = title,
            url = url,
            datetime = time,
            officeHname = press,
            thumbUrl = thumbnailUrl,
            badgeLabel = category,
            badgeColor = "gray"
            ,
            sectionKey = category,
            sectionLabel = category
        )
    }
}

/**
 * 머니스토리 카드입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverMoneyStoryArticle(
    val url: String? = null,
    val photo: NaverMoneyStoryPhoto? = null,
    val title: String = "",
    val categoryName: String? = null,
    val date: String? = null,
    val viewCount: Long? = null
) {

    /**
     * 공통 기사 모델로 변환합니다.
     */
    fun toNewsArticle(): NaverNewsArticle {
        return NaverNewsArticle(
            title = title,
            url = url,
            datetime = date,
            officeHname = categoryName,
            thumbUrl = photo?.src,
            badgeLabel = "Story",
            badgeColor = "green",
            sectionKey = "STORY",
            sectionLabel = "Story"
        )
    }
}

/**
 * 머니스토리 사진 정보입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverMoneyStoryPhoto(
    val src: String? = null,
    val alt: String? = null
)

/**
 * 공지 리스트 요약 항목입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNoticeSummary(
    val noticeId: String,
    val title: String,
    val category: String? = null,
    val categoryColor: String? = null,
    val createdAt: String? = null
) {

    /**
     * 공통 기사 모델로 변환합니다.
     */
    fun toNewsArticle(): NaverNewsArticle {
        return NaverNewsArticle(
            title = title,
            datetime = createdAt,
            officeHname = category,
            badgeLabel = category ?: "공지",
            badgeColor = categoryColor,
            sectionKey = "NOTICE",
            sectionLabel = category ?: "공지",
            isNotice = true
        )
    }
}

/**
 * 거래소 공지 섹션입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverExchangeNoticeSection(
    val items: List<NaverExchangeNoticeArticle> = emptyList(),
    val totalElements: String? = null
)

/**
 * 거래소 공지 상세 항목입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverExchangeNoticeArticle(
    val no: String? = null,
    val comment: String? = null,
    val datetime: String? = null,
    val title: String = "",
    val itemName: String? = null,
    val itemcode: String? = null,
    val causeCode: String? = null,
    val noticeTypeName: String? = null,
    val contents: String? = null
) {

    /**
     * 공통 기사 모델로 변환합니다.
     */
    fun toNewsArticle(): NaverNewsArticle {
        return NaverNewsArticle(
            title = title,
            datetime = datetime,
            officeHname = noticeTypeName ?: comment,
            subcontent = contents?.stripHtml(),
            badgeLabel = noticeTypeName ?: "거래소",
            badgeColor = "red",
            sectionKey = "EXCHANGE_NOTICE",
            sectionLabel = noticeTypeName ?: "거래소",
            isNotice = true
        )
    }
}

private fun String.stripHtml(): String {
    return replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
