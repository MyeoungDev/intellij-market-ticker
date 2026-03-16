package com.github.myeoungdev.marketticker.domain.model.research

enum class ResearchCategory(val code: String, val label: String) {
    MARKET("MARKET", "시황정보"),
    COMPANY("COMPANY", "종목분석"),
    INDUSTRY("INDUSTRY", "산업분석"),
    INVEST("INVEST", "투자전략"),
    ECONOMY("ECONOMY", "경제분석"),
    DEBENTURE("DEBENTURE", "채권분석");

    override fun toString(): String = label
}

enum class ResearchRankingType(val code: String, val label: String) {
    SEARCH_TOP("SEARCH_TOP", "검색상위"),
    PRICE_TOP("PRICE_TOP", "거래대금상위"),
    UP("UP", "상승"),
    DOWN("DOWN", "하락");

    override fun toString(): String = label
}

data class ResearchArticle(
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

data class ResearchRankingItem(
    val itemName: String = "",
    val itemCode: String = "",
    val marketStatus: String = "",
    val nowVal: String = "",
    val changeRate: String = "",
    val per: String = "",
    val pbr: String = "",
    val dividendRate: String = "",
    val marketSum: String = ""
)

data class ResearchRankingBundle(
    val ranking: List<ResearchRankingItem> = emptyList(),
    val latestResearch: List<ResearchArticle> = emptyList()
)
