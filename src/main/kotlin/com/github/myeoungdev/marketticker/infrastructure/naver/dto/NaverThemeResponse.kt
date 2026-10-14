package com.github.myeoungdev.marketticker.infrastructure.naver.dto

data class NaverThemeRankingPage(
    val hasNext: Boolean,
    val items: List<NaverThemeRankingItem>,
    val cursor: String? = null
)

data class NaverThemeRankingItem(
    val code: String? = null,
    val name: String? = null,
    val changeRate: String? = null,
    val risingCount: String? = null,
    val fallingCount: String? = null,
    val unchangedCount: String? = null,
    val updatedAt: String? = null,
    val topByChangeRate: List<NaverThemeLeader?>? = null
)

data class NaverThemeLeader(
    val code: String? = null,
    val name: String? = null,
    val value: String? = null
)

data class NaverThemeMember(
    val itemcode: String? = null,
    val itemname: String? = null,
    val sosok: String? = null,
    val nowPrice: String? = null,
    val prevChangeRate: String? = null,
    val tradeVolume: String? = null
)
