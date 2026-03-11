package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverResearchAggregateResponse(
    @JsonProperty("researchCategory")
    val researchCategory: List<NaverResearchAggregateSection> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverResearchAggregateSection(
    val url: String = "",
    val category: String = "",
    val report: List<NaverResearchAggregateItem> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverResearchAggregateItem(
    val url: String = "",
    val title: String = "",
    val iconName: String = "",
    val securitiesCompany: String = "",
    val publishDate: String = "",
    val analystName: List<String> = emptyList(),
    val isToday: Boolean = false
) {
    fun toArticle(categoryName: String): NaverResearchArticle {
        return NaverResearchArticle(
            researchCategory = categoryName,
            category = categoryName,
            researchId = url.substringAfterLast('/'),
            title = title,
            content = "",
            brokerName = securitiesCompany,
            writeDate = publishDate,
            endUrl = if (url.startsWith("http")) url else "https://m.stock.naver.com$url",
            analyst = analystName.joinToString(",").ifBlank { null }
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverResearchArticle(
    val researchCategory: String = "",
    val category: String = "",
    val itemCode: String? = null,
    val itemName: String? = null,
    val researchId: String = "",
    val title: String = "",
    val content: String = "",
    val brokerName: String = "",
    val brokerCode: String? = null,
    val writeDate: String = "",
    val readCount: String? = null,
    val endUrl: String = "",
    val opinion: String? = null,
    val goalPrice: String? = null,
    val prevGoalPrice: String? = null,
    val analyst: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverStockResearchItem(
    @JsonProperty("nid")
    val nid: String = "",
    @JsonProperty("itemcode")
    val itemCode: String = "",
    @JsonProperty("itemname")
    val itemName: String = "",
    val brokerName: String = "",
    val brokerCode: String? = null,
    val title: String = "",
    val content: String = "",
    val goalPrice: String? = null,
    val opinion: String? = null,
    val attachUrl: String = "",
    val readCount: String? = null,
    val writeDate: String = "",
    val prevGoalPrice: String? = null
) {
    fun toArticle(): NaverResearchArticle {
        return NaverResearchArticle(
            researchCategory = "종목 리서치",
            category = "종목 리서치",
            itemCode = itemCode,
            itemName = itemName,
            researchId = nid,
            title = title,
            content = content,
            brokerName = brokerName,
            brokerCode = brokerCode,
            writeDate = writeDate,
            readCount = readCount,
            endUrl = attachUrl,
            opinion = opinion,
            goalPrice = goalPrice,
            prevGoalPrice = prevGoalPrice
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverResearchLatestResponse(
    @JsonProperty("MARKET")
    val market: List<NaverResearchArticle> = emptyList(),
    @JsonProperty("COMPANY")
    val company: List<NaverResearchArticle> = emptyList(),
    @JsonProperty("INDUSTRY")
    val industry: List<NaverResearchArticle> = emptyList(),
    @JsonProperty("INVEST")
    val invest: List<NaverResearchArticle> = emptyList(),
    @JsonProperty("ECONOMY")
    val economy: List<NaverResearchArticle> = emptyList(),
    @JsonProperty("DEBENTURE")
    val debenture: List<NaverResearchArticle> = emptyList()
) {
    fun categoryMap(): Map<ResearchCategoryKey, List<NaverResearchArticle>> {
        return linkedMapOf(
            ResearchCategoryKey.MARKET to market,
            ResearchCategoryKey.COMPANY to company,
            ResearchCategoryKey.INDUSTRY to industry,
            ResearchCategoryKey.INVEST to invest,
            ResearchCategoryKey.ECONOMY to economy,
            ResearchCategoryKey.DEBENTURE to debenture
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDiscussionRankingResponse(
    val rankTime: String = "",
    val totalCount: Int = 0,
    val contents: List<NaverDiscussionRankingItem> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDiscussionRankingItem(
    val itemCode: String = "",
    val ranking: Int = 0,
    val prevRanking: Int? = null,
    val score: Int = 0,
    val rankTime: String = "",
    val posts: List<NaverDiscussionPost> = emptyList(),
    val stockPrices: NaverDiscussionStockPrice? = null
) {
    fun toArticle(): NaverResearchArticle {
        val stockName = stockPrices?.stockName?.ifBlank { null } ?: itemCode
        val topPost = posts.firstOrNull()
        val premarket = stockPrices?.overMarketPriceInfo?.fluctuationsRatio
            ?.takeIf { it.isNotBlank() }
            ?.let { "프리마켓 ${if (it.startsWith("-")) "" else "+"}$it%" }

        val summary = listOfNotNull(
            "랭킹 ${ranking}위",
            "점수 $score",
            premarket
        ).joinToString(" · ")

        val previewBody = buildString {
            append("<p><strong>${escapeHtml(topPost?.title ?: stockName)}</strong></p>")
            if (summary.isNotBlank()) {
                append("<p>$summary</p>")
            }
            topPost?.contentSwReplacedButImg
                ?.takeIf { it.isNotBlank() }
                ?.let { append("<p>${escapeHtml(it)}</p>") }
        }

        return NaverResearchArticle(
            researchCategory = "커뮤니티 HOT",
            category = "커뮤니티 HOT",
            itemCode = itemCode,
            itemName = stockName,
            researchId = topPost?.postId ?: "$itemCode-$ranking",
            title = "[${ranking}위] $stockName - ${topPost?.title ?: "토론 급상승"}",
            content = previewBody,
            brokerName = "네이버 커뮤니티",
            writeDate = rankTime.substringBefore("T"),
            endUrl = stockPrices?.endUrl.orEmpty(),
            analyst = summary.ifBlank { null }
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDiscussionPost(
    val postId: String = "",
    val title: String = "",
    val contentSwReplacedButImg: String = "",
    val postType: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDiscussionStockPrice(
    val stockName: String = "",
    val endUrl: String = "",
    val overMarketPriceInfo: NaverDiscussionOverMarketPriceInfo? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverDiscussionOverMarketPriceInfo(
    val overPrice: String = "",
    val fluctuationsRatio: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverResearchRankingResponse(
    val ranking: List<NaverResearchRankingItem> = emptyList(),
    val latestResearch: List<NaverResearchArticle> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverResearchRankingItem(
    @JsonProperty("itemname")
    val itemName: String = "",
    @JsonProperty("itemcode")
    val itemCode: String = "",
    @JsonProperty("marketStatus")
    val marketStatus: String = "",
    @JsonProperty("nowVal")
    val nowVal: String = "",
    @JsonProperty("changeRate")
    val changeRate: String = "",
    @JsonProperty("per")
    val per: String = "",
    @JsonProperty("pbr")
    val pbr: String = "",
    @JsonProperty("dividendRate")
    val dividendRate: String = "",
    @JsonProperty("marketSum")
    val marketSum: String = ""
)

enum class ResearchRankingType(val code: String, val label: String) {
    SEARCH_TOP("SEARCH_TOP", "검색상위"),
    PRICE_TOP("PRICE_TOP", "거래대금상위"),
    UP("UP", "상승"),
    DOWN("DOWN", "하락");

    override fun toString(): String = label
}

private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

enum class ResearchCategoryKey(val code: String, val label: String) {
    MARKET("MARKET", "시황정보"),
    COMPANY("COMPANY", "종목분석"),
    INDUSTRY("INDUSTRY", "산업분석"),
    INVEST("INVEST", "투자전략"),
    ECONOMY("ECONOMY", "경제분석"),
    DEBENTURE("DEBENTURE", "채권분석");

    override fun toString(): String = label
}
