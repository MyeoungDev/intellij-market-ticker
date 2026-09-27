package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.news.HeadlineNewsBundle
import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.github.myeoungdev.marketticker.domain.model.news.NewsCategoryLoadState
import com.github.myeoungdev.marketticker.domain.model.news.NewsCategoryPage
import com.github.myeoungdev.marketticker.domain.model.news.NewsLoadStatus
import com.github.myeoungdev.marketticker.domain.model.news.TickerNewsBundle
import com.github.myeoungdev.marketticker.domain.model.news.TickerOverviewCard
import com.github.myeoungdev.marketticker.application.provider.NewsProvider
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCoinOverview
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverDomesticStockDetail
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockBasic
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockOverview
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverNewsArticle
import java.time.Clock
import java.time.Instant

/**
 * Naver 기반 뉴스/종목 개요 provider 구현체입니다.
 */
class NaverNewsProvider(
    private val client: NaverClient = NaverClient(),
    private val clock: Clock = Clock.systemUTC()
) : NewsProvider {

    fun getHeadlineNews(): HeadlineNewsBundle = getHeadlineNews(pageSize = 15)

    override fun getHeadlineNews(pageSize: Int): HeadlineNewsBundle {
        val flash = loadCategoryPage("FLASHNEWS", page = 1, pageSize = pageSize)
        val main = loadCategoryPage("MAINNEWS", page = 1, pageSize = pageSize)
        val world = loadCategoryPage("WORLDNEWS", page = 1, pageSize = pageSize)
        val focusSpecs = listOf(
            401 to "시장전망",
            402 to "기업분석",
            403 to "글로벌시장",
            404 to "채권/선물",
            406 to "공시메모",
            429 to "환율"
        )
        val focusLoads = focusSpecs.map { (sid, label) ->
            val result = client.fetchNewsFocusResult(sid = sid, page = 1, pageSize = 5)
            Triple(sid, label, result)
        }
        val focusSections = focusLoads.mapNotNull { (sid, label, loaded) ->
            loaded.value.articles.takeIf { it.isNotEmpty() }?.let { articles ->
                com.github.myeoungdev.marketticker.domain.model.news.NewsSection(
                    title = label,
                    key = sid.toString(),
                    articles = articles.map {
                        it.toNewsArticle(label).toAppNewsArticle(
                            badgeLabel = label,
                            badgeColor = "gray",
                            sectionLabel = label
                        )
                    }
                )
            }
        }
        val moneyResult = client.fetchMoneyStory(mainCategoryIdList = 1, size = 20)
        val moneyStories = moneyResult.value.moneyContentList.map {
            it.toNewsArticle().toAppNewsArticle(
                badgeLabel = "머니스토리",
                badgeColor = "green",
                sectionLabel = "머니스토리"
            )
        }
        val noticeResult = client.fetchNoticePage(page = 1, pageSize = 10)
        val notices = noticeResult.value.content.map {
            it.toNewsArticle().toAppNewsArticle(
                badgeLabel = it.category ?: "공지",
                badgeColor = "red",
                sectionLabel = "공지"
            )
        }

        return HeadlineNewsBundle(
            headlines = linkedMapOf(
                "FLASHNEWS" to flash.articles,
                "MAINNEWS" to main.articles,
                "WORLDNEWS" to world.articles
            ),
            worldNews = world.articles.take(6),
            moneyStories = moneyStories,
            focusSections = focusSections,
            notices = notices,
            categoryStates = linkedMapOf(
                "FLASHNEWS" to flash.state,
                "MAINNEWS" to main.state,
                "WORLDNEWS" to world.state
            ).apply {
                focusLoads.forEach { (sid, _, loaded) -> put("FOCUS_$sid", loaded.toNewsCategoryLoadState()) }
                put("MONEY", moneyResult.toNewsCategoryLoadState())
                put("NOTICE", noticeResult.toNewsCategoryLoadState())
            }
        )
    }

    private fun <T> NaverFetchResult<T>.toNewsCategoryLoadState(): NewsCategoryLoadState {
        return when (status) {
            NaverFetchStatus.SUCCESS -> NewsCategoryLoadState(NewsLoadStatus.SUCCESS, message)
            NaverFetchStatus.EMPTY -> NewsCategoryLoadState(NewsLoadStatus.EMPTY, message)
            NaverFetchStatus.FAILED -> NewsCategoryLoadState(NewsLoadStatus.FAILED, message)
        }
    }

    override fun getMostViewedNews(limit: Int): List<NewsArticle> {
        return getMostViewedNewsPage(limit).articles
    }

    override fun getMostViewedNewsPage(limit: Int): NewsCategoryPage {
        return loadCategoryPage("RANKNEWS", page = 1, pageSize = limit.coerceIn(1, 30))
    }

    override fun getCategoryNews(categoryKey: String, page: Int, pageSize: Int): List<NewsArticle> {
        return getCategoryNewsPage(categoryKey, page, pageSize).articles
    }

    override fun getCategoryNewsPage(categoryKey: String, page: Int, pageSize: Int): NewsCategoryPage {
        return loadCategoryPage(categoryKey, page, pageSize)
    }

    override fun getTickerNews(ticker: Ticker): TickerNewsBundle {
        val overview = if (!ticker.marketType.isKoreanMarket() && !ticker.marketType.isCryptoMarket()) {
            client.fetchForeignStockOverview(ticker.tradingSymbol)
        } else {
            null
        }
        val basic = if (!ticker.marketType.isKoreanMarket() && !ticker.marketType.isCryptoMarket()) {
            client.fetchForeignStockBasic(ticker.tradingSymbol)
        } else {
            null
        }
        val domesticDetail = if (ticker.marketType.isKoreanMarket()) {
            client.fetchDomesticStockDetail(ticker.symbol)
        } else {
            null
        }
        val coinOverview = if (ticker.marketType.isCryptoMarket()) {
            client.fetchCoinOverview(ticker.marketType.name, ticker.symbol)
        } else {
            null
        }

        val articles = if (ticker.marketType.isCryptoMarket()) {
            searchNews(buildCryptoNewsQuery(ticker, coinOverview), page = 1, pageSize = 7)
        } else {
            val localItemCode = if (ticker.marketType.isKoreanMarket()) ticker.symbol else ticker.tradingSymbol
            val domesticArticles = client.fetchDomesticDetailNews(itemCode = localItemCode, page = 1, pageSize = 15)
                .map { it.toAppNewsArticle() }
            val foreignArticles = if (ticker.marketType.isKoreanMarket()) {
                emptyList()
            } else {
                client.fetchForeignStockNews(reutersCode = ticker.tradingSymbol, page = 1, pageSize = 15)
                    .map { it.toAppNewsArticle() }
            }
            mergeArticles(domesticArticles, foreignArticles)
        }

        return TickerNewsBundle(
            overviewCard = buildOverviewCard(ticker, overview, basic, coinOverview, domesticDetail),
            articles = articles
        )
    }

    override fun searchNews(query: String, page: Int, pageSize: Int): List<NewsArticle> {
        return client.fetchNewsSearch(query, page, pageSize).map { it.toAppNewsArticle() }
    }

    private fun NaverNewsArticle.toAppNewsArticle(
        badgeLabel: String? = this.badgeLabel,
        badgeColor: String? = this.badgeColor,
        sectionLabel: String? = this.sectionLabel
    ): NewsArticle {
        val resolvedUrl = articleUrl()
        val normalizedTimestamp = NaverNewsTimestampNormalizer.normalize(datetime, clock)
        return NewsArticle(
            id = resolvedUrl ?: listOfNotNull(officeId, articleId, title).joinToString(":"),
            title = title,
            summary = subcontent.orEmpty(),
            source = officeHname.orEmpty(),
            publishedAt = normalizedTimestamp.displayText,
            publishedAtInstant = normalizedTimestamp.instant,
            url = resolvedUrl,
            thumbnailUrl = thumbUrl,
            sectionLabel = sectionLabel.orEmpty(),
            badgeLabel = badgeLabel.orEmpty(),
            badgeColor = badgeColor,
            ranking = ranking
        )
    }

    private fun mergeArticles(
        domesticArticles: List<NewsArticle>,
        foreignArticles: List<NewsArticle>,
        limit: Int = 5
    ): List<NewsArticle> {
        return (domesticArticles + foreignArticles)
            .distinctBy { article -> "${article.url}::${article.title}" }
            .sortedWith(
                compareByDescending<NewsArticle> { article ->
                    article.publishedAtInstant ?: Instant.EPOCH
                }.thenByDescending { it.publishedAt }
            )
            .take(limit)
    }

    private fun loadCategoryPage(categoryKey: String, page: Int, pageSize: Int): NewsCategoryPage {
        val normalizedKey = categoryKey.uppercase()
        val result = when (normalizedKey) {
            "FLASHNEWS", "MAINNEWS", "RANKNEWS" -> client.fetchNewsListResult(
                category = normalizedKey,
                page = page,
                pageSize = pageSize
            )
            "WORLDNEWS" -> client.fetchWorldNewsResult(page = page, pageSize = pageSize)
            else -> return NewsCategoryPage()
        }

        return NewsCategoryPage(
            articles = result.articles.map {
                when (normalizedKey) {
                    "FLASHNEWS" -> it.toAppNewsArticle(
                        badgeLabel = "Flash",
                        badgeColor = "red",
                        sectionLabel = "Flash"
                    )
                    "MAINNEWS" -> it.toAppNewsArticle(
                        badgeLabel = "Main",
                        badgeColor = "blue",
                        sectionLabel = "Main"
                    )
                    "WORLDNEWS" -> it.toAppNewsArticle(
                        badgeLabel = "Global",
                        badgeColor = "gray",
                        sectionLabel = "해외 뉴스"
                    )
                    else -> it.toAppNewsArticle()
                }
            },
            state = NewsCategoryLoadState(result.status, result.message)
        )
    }

    private fun buildCryptoNewsQuery(
        ticker: Ticker,
        coinOverview: NaverCoinOverview?
    ): String {
        val aliases = CRYPTO_NEWS_ALIASES[ticker.symbol.uppercase()].orEmpty()
        return buildList {
            addAll(aliases)
            add(ticker.name)
            add(ticker.symbol)
            add(ticker.tradingSymbol.substringBefore('_'))
            coinOverview?.krName?.let(::add)
            coinOverview?.enName?.let(::add)
            coinOverview?.exchangeTicker?.let(::add)
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" | ")
    }

    private fun buildOverviewCard(
        ticker: Ticker,
        overview: NaverForeignStockOverview?,
        basic: NaverForeignStockBasic?,
        coinOverview: NaverCoinOverview?,
        domesticDetail: NaverDomesticStockDetail?
    ): TickerOverviewCard? {
        if (ticker.marketType.isCryptoMarket()) {
            return buildCryptoOverviewCard(ticker, coinOverview)
        }

        if (ticker.marketType.isKoreanMarket()) {
            return buildDomesticOverviewCard(ticker, domesticDetail)
        }

        if (overview == null && basic == null) return null

        val title = listOfNotNull(
            overview?.companyName?.takeIf { it.isNotBlank() },
            overview?.companyNameEng?.takeIf { it.isNotBlank() },
            basic?.stockName?.takeIf { it.isNotBlank() },
            basic?.stockNameEng?.takeIf { it.isNotBlank() }
        ).distinct().joinToString(" / ")

        val metaPrimary = listOfNotNull(
            overview?.stockItemListedInfo?.stockExchange ?: basic?.stockExchangeName,
            overview?.industry?.industryGroupKor ?: basic?.industryCodeType?.industryGroupKor
        ).joinToString(" · ")

        val primaryMetrics = listOfNotNull(
            basic?.closePrice?.takeIf { it.isNotBlank() }?.let { "현재가 $it" },
            basic?.fluctuationsRatio?.takeIf { it.isNotBlank() }?.let { "등락률 ${it}%" },
            metricValue(basic, "marketValue")?.let { "시총 ${metricValueDesc(basic, "marketValue") ?: it}" }
                ?: overview?.stockItemListedInfo?.marketValueKrw?.let { "시총 $it" },
            metricValue(basic, "per")?.let { "PER $it" },
            metricValue(basic, "pbr")?.let { "PBR $it" }
        ).joinToString(" · ")

        val secondaryMetrics = listOfNotNull(
            metricValue(basic, "highPriceOf52Weeks")?.let { high ->
                metricValue(basic, "lowPriceOf52Weeks")?.let { low -> "52주 $low - $high" }
            }
        ).joinToString(" · ")

        val summary = overview?.summaries?.summary
            ?.takeIf { it.isNotBlank() }
            ?.let(::plainText)
            ?: overview?.summary?.let(::plainText)
            ?: ""

        return TickerOverviewCard(
            title = title,
            metaPrimary = metaPrimary,
            metaSecondary = "",
            primaryMetrics = primaryMetrics,
            secondaryMetrics = secondaryMetrics,
            summary = clipSummary(summary),
            siteUrl = overview?.summaries?.url ?: basic?.endUrl
        )
    }

    private fun buildCryptoOverviewCard(
        ticker: Ticker,
        coinOverview: NaverCoinOverview?
    ): TickerOverviewCard? {
        val overview = coinOverview ?: return null
        val title = listOfNotNull(
            overview.krName.takeIf { it.isNotBlank() },
            overview.enName?.takeIf { it.isNotBlank() }
        ).joinToString(" / ")

        val metaPrimary = listOfNotNull(
            overview.exchangeName?.takeIf { it.isNotBlank() } ?: ticker.marketType.displayName,
            overview.exchangeType.takeIf { it.isNotBlank() }
        ).joinToString(" · ")

        val primaryMetrics = listOfNotNull(
            "현재가 ${formatCoinNumber(overview.tradePrice)}",
            overview.profileInfo?.marketCap?.toLong()?.let { "시총 ${formatLargeKrw(it)}" },
            "${formatSignedPercent(overview.changeRate)} · ${formatSignedNumber(overview.changeValue)} KRW",
            overview.krwPremiumRate?.let { "김프 ${formatSignedPercent(it)}" }
        ).joinToString(" · ")

        val secondaryMetrics = listOfNotNull(
            coinTotalInfoValue(overview, "highest52weekPrice")?.let { high ->
                coinTotalInfoValue(overview, "lowest52weekPrice")?.let { low ->
                    "52주 ${formatCoinNumber(low)} - ${formatCoinNumber(high)}"
                }
            }
        ).joinToString(" · ")

        return TickerOverviewCard(
            title = title.ifBlank { ticker.name },
            metaPrimary = metaPrimary,
            metaSecondary = "",
            primaryMetrics = primaryMetrics,
            secondaryMetrics = secondaryMetrics,
            summary = clipSummary(plainText(overview.profileInfo?.contentKr.orEmpty())),
            siteUrl = null
        )
    }

    private fun buildDomesticOverviewCard(
        ticker: Ticker,
        domesticDetail: NaverDomesticStockDetail?
    ): TickerOverviewCard? {
        domesticDetail?.let { detail ->
            return TickerOverviewCard(
                title = detail.itemname?.takeIf { it.isNotBlank() } ?: ticker.name,
                metaPrimary = listOfNotNull(
                    ticker.nationName,
                    ticker.marketType.name,
                    detail.upJongName?.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                metaSecondary = "",
                primaryMetrics = listOfNotNull(
                    detail.nowVal?.takeIf { it.isNotBlank() }?.let { "현재가 $it" },
                    detail.changeRate?.takeIf { it.isNotBlank() }?.let { "등락률 ${it}%" },
                    detail.marketSum?.takeIf { it.isNotBlank() }?.let { "시총 ${formatKrwValue(it)}" },
                    detail.per?.takeIf { it.isNotBlank() }?.let { "PER ${it}배" },
                    detail.pbr?.takeIf { it.isNotBlank() }?.let { "PBR ${it}배" }
                ).joinToString(" · "),
                secondaryMetrics = listOfNotNull(
                    detail.eps?.takeIf { it.isNotBlank() }?.let { "EPS ${it}" },
                    detail.high52week?.takeIf { it.isNotBlank() }?.let { high ->
                        detail.low52week?.takeIf { it.isNotBlank() }?.let { low -> "52주 $low - $high" }
                    }
                ).joinToString(" · "),
                summary = clipSummary(plainText(detail.summaryText())),
                siteUrl = null
            )
        }

        return TickerOverviewCard(
            title = ticker.name,
            metaPrimary = listOfNotNull(ticker.nationName, ticker.marketType.name).joinToString(" · "),
            metaSecondary = "",
            primaryMetrics = "",
            secondaryMetrics = "",
            summary = "국내 종목 개요 정보가 제한되어 있습니다.",
            siteUrl = null
        )
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

    private fun clipSummary(text: String, maxLength: Int = 180): String {
        val normalized = text.trim()
        if (normalized.length <= maxLength) return normalized
        return normalized.take(maxLength).trimEnd() + "..."
    }

    private fun metricValue(basic: NaverForeignStockBasic?, code: String): String? {
        return basic?.stockItemTotalInfos?.firstOrNull { it.code == code }?.value?.takeIf { it.isNotBlank() }
    }

    private fun metricValueDesc(basic: NaverForeignStockBasic?, code: String): String? {
        return basic?.stockItemTotalInfos?.firstOrNull { it.code == code }?.valueDesc?.takeIf { it.isNotBlank() }
    }

    private fun coinTotalInfoValue(overview: NaverCoinOverview, code: String): Double? {
        return overview.totalInfos.firstOrNull { it.code == code }?.value
    }

    private fun formatKrwValue(value: String): String {
        val amount = value.filter(Char::isDigit)
        if (amount.isBlank()) return value
        val numeric = amount.toLongOrNull() ?: return value
        val eok = numeric / 100_000_000L
        val jo = eok / 10_000L
        val remainEok = eok % 10_000L
        return when {
            jo > 0L && remainEok > 0L -> "${jo}조 ${formatNumber(remainEok)}억원"
            jo > 0L -> "${jo}조원"
            else -> "${formatNumber(eok)}억원"
        }
    }

    private fun formatLargeKrw(value: Long): String {
        val eok = value / 100_000_000L
        val jo = eok / 10_000L
        val remainEok = eok % 10_000L
        return when {
            jo > 0L && remainEok > 0L -> "${jo}조 ${formatNumber(remainEok)}억원"
            jo > 0L -> "${jo}조원"
            else -> "${formatNumber(eok)}억원"
        }
    }

    private fun formatSignedPercent(value: Number?): String {
        if (value == null) return "0.00%"
        val number = value.toDouble()
        return if (number > 0) "+${formatPercent(number)}" else "${formatPercent(number)}"
    }

    private fun formatSignedNumber(value: Number?): String {
        if (value == null) return "0"
        val number = value.toLong()
        val formatted = formatNumber(kotlin.math.abs(number))
        return when {
            number > 0 -> "+$formatted"
            number < 0 -> "-$formatted"
            else -> formatted
        }
    }

    private fun formatPercent(value: Double): String {
        return String.format(java.util.Locale.US, "%.2f%%", value)
    }

    private fun formatCoinNumber(value: Number): String {
        return formatNumber(value.toLong())
    }

    private fun formatNumber(value: Long): String {
        return java.text.NumberFormat.getIntegerInstance().format(value)
    }

    companion object {
        private val CRYPTO_NEWS_ALIASES = mapOf(
            "BTC" to listOf("비트코인", "BTC", "Bitcoin"),
            "ETH" to listOf("이더리움", "ETH", "Ethereum"),
            "XRP" to listOf("엑스알피", "리플", "XRP", "Ripple"),
            "DOGE" to listOf("도지코인", "DOGE", "Dogecoin"),
            "MATIC" to listOf("폴리곤", "MATIC", "Polygon")
        )
    }
}
