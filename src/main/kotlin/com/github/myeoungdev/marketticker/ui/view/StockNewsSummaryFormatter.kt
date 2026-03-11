package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockBasic
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockOverview
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverNewsArticle

/**
 * 하단 종목 뉴스 요약 패널용 표시 데이터입니다.
 */
internal data class StockNewsOverviewCard(
    val title: String,
    val meta: String,
    val metrics: String,
    val summary: String,
    val siteUrl: String?
)

/**
 * 하단 종목 뉴스 요약 패널의 표시용 데이터 조합기입니다.
 */
internal object StockNewsSummaryFormatter {

    fun buildOverviewCard(
        overview: NaverForeignStockOverview?,
        basic: NaverForeignStockBasic?
    ): StockNewsOverviewCard? {
        if (overview == null && basic == null) return null

        val title = listOfNotNull(
            overview?.companyName?.takeIf { it.isNotBlank() },
            overview?.companyNameEng?.takeIf { it.isNotBlank() },
            basic?.stockName?.takeIf { it.isNotBlank() },
            basic?.stockNameEng?.takeIf { it.isNotBlank() }
        ).distinct().joinToString(" / ")

        val meta = listOfNotNull(
            overview?.stockItemListedInfo?.stockExchange ?: basic?.stockExchangeName,
            overview?.industry?.industryGroupKor ?: basic?.industryCodeType?.industryGroupKor,
            metricValue(basic, "marketValue")?.let { metricValueDesc(basic, "marketValue") ?: it }
                ?: overview?.stockItemListedInfo?.marketValueKrw,
            overview?.summaries?.representativeName
        ).joinToString(" · ")

        val metrics = listOfNotNull(
            basic?.closePrice?.let { price ->
                basic.fluctuationsRatio?.let { ratio ->
                    "${price}${basic.currencyType?.code?.let { " $it" }.orEmpty()} (${ratio}%)"
                }
            },
            metricValue(basic, "per")?.let { "PER $it" },
            metricValue(basic, "pbr")?.let { "PBR $it" },
            metricValue(basic, "highPriceOf52Weeks")?.let { high ->
                metricValue(basic, "lowPriceOf52Weeks")?.let { low -> "52주 $low - $high" }
            },
            metricValue(basic, "dividendYieldRatio")?.let { "배당 $it" }
        ).joinToString(" · ")

        val summary = overview?.summaries?.summary
            ?.takeIf { it.isNotBlank() }
            ?.let(::plainText)
            ?: overview?.summary?.let(::plainText)
            ?: ""

        return StockNewsOverviewCard(
            title = title,
            meta = meta,
            metrics = metrics,
            summary = summary,
            siteUrl = overview?.summaries?.url ?: basic?.endUrl
        )
    }

    fun mergeArticles(
        domesticArticles: List<NaverNewsArticle>,
        foreignArticles: List<NaverNewsArticle>,
        limit: Int = 5
    ): List<NaverNewsArticle> {
        return (domesticArticles + foreignArticles)
            .distinctBy { article -> "${article.articleUrl()}::${article.title}" }
            .sortedByDescending { article -> article.datetime.orEmpty().filter(Char::isDigit) }
            .take(limit)
    }

    private fun plainText(text: String): String {
        return text.replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun metricValue(basic: NaverForeignStockBasic?, code: String): String? {
        return basic?.stockItemTotalInfos?.firstOrNull { it.code == code }?.value?.takeIf { it.isNotBlank() }
    }

    private fun metricValueDesc(basic: NaverForeignStockBasic?, code: String): String? {
        return basic?.stockItemTotalInfos?.firstOrNull { it.code == code }?.valueDesc?.takeIf { it.isNotBlank() }
    }
}
