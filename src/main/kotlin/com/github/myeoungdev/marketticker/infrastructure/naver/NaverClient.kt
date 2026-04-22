package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.application.service.PriceHistoryService
import com.github.myeoungdev.marketticker.common.config.httpClient
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCoinPrice
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCryptoCandle
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCryptoChartResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCoinRankResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverDomesticMarketStockItem
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignMarketStockItem
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverRealtimeCoinPriceResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverRealTimeStockPriceResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverMarketIndicatorResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverDiscussionRankingResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchAggregateResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchArticle
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchLatestResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchRankingResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverStockResearchItem
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchRankingType
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverSearchItem
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverSearchResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverStockChartCandle
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverStockPrice
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.*
import com.intellij.openapi.application.ApplicationManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Naver 증권/마켓 API를 호출하는 저수준 HTTP 클라이언트입니다.
 *
 * 이 클래스는 검색, 시세, 차트, 뉴스, 리서치처럼 외부 UI에서 직접 사용하는
 * 원시 데이터를 조회하며, 모든 네트워크 호출은 EDT 밖에서만 수행되어야 합니다.
 *
 * 반환 규칙:
 * - 조회 실패 시 리스트 계열은 가능한 한 `emptyList()`를 반환합니다.
 * - 단건 조회 실패 시 `null`을 반환합니다.
 * - UI 레이어는 이 반환값을 그대로 사용해 fallback 또는 빈 상태를 렌더링합니다.
 */
private val logger = KotlinLogging.logger {}

class NaverClient(
    private val client: HttpClient = httpClient,
    private val searchBaseUrl: String = "https://stock.naver.com/api/autocomplete/search/autoComplete",
    private val domesticPriceUrl: String = "https://polling.finance.naver.com/api/realtime/domestic/stock",
    private val worldPriceUrl: String = "https://polling.finance.naver.com/api/realtime/worldstock/stock",
    private val coinPriceUrl: String = "https://polling.finance.naver.com/api/realtime/coin/price",
    private val coinOverviewUrl: String = "https://stock.naver.com/api/coin/price",
    private val cryptoChartUrl: String = "https://m.stock.naver.com/front-api/chart/cryptoChartData",
    private val domesticIndexUrl: String = "https://stock.naver.com/api/polling/domestic/index",
    private val worldIndexUrl: String = "https://stock.naver.com/api/polling/worldstock/index",
    private val marketMetalUrl: String = "https://stock.naver.com/api/polling/marketindex/metals",
    private val marketEnergyUrl: String = "https://stock.naver.com/api/polling/marketindex/energy",
    private val exchangeRateUrl: String = "https://stock.naver.com/api/domestic/exchange/List",
    private val domesticChartUrl: String = "https://api.stock.naver.com/chart/domestic/item",
    private val foreignChartUrl: String = "https://api.stock.naver.com/chart/foreign/item",
    private val researchAggregateUrl: String = "https://stock.naver.com/api/domestic/home/researchaggregate/static",
    private val researchRecentPopularUrl: String = "https://stock.naver.com/api/domestic/research/recent-popular",
    private val researchCategoryLatestUrl: String = "https://stock.naver.com/api/domestic/research/category-lastest",
    private val industryResearchUrl: String = "https://stock.naver.com/api/domestic/research/industry-research",
    private val discussionRankingUrl: String = "https://stock.naver.com/api/community/discussion/rankings",
    private val researchRankingUrl: String = "https://stock.naver.com/api/domestic/research/ranking",
    private val stockResearchBaseUrl: String = "https://stock.naver.com/api/domestic/research",
    private val newsListUrl: String = "https://stock.naver.com/api/domestic/news/list",
    private val worldNewsUrl: String = "https://stock.naver.com/api/foreign/news/worldNews",
    private val domesticDetailNewsUrl: String = "https://stock.naver.com/api/domestic/detail/news",
    private val domesticStockDetailUrl: String = "https://stock.naver.com/api/domestic/detail",
    private val foreignStockNewsUrl: String = "https://stock.naver.com/api/foreign/worldStock/list",
    private val foreignStockOverviewUrl: String = "https://stock.naver.com/api/securityService/stock",
    private val foreignStockBasicUrl: String = "https://stock.naver.com/api/securityService/stock",
    private val newsAggregateUrl: String = "https://stock.naver.com/api/domestic/news/aggregate/home",
    private val noticeListUrl: String = "https://stock.naver.com/api/domestic/home/noticeList",
    private val newsSearchUrl: String = "https://stock.naver.com/api/domestic/news/search",
    private val domesticMarketStockDefaultUrl: String = "https://stock.naver.com/api/domestic/market/stock/default",
    private val foreignMarketStockGlobalUrl: String = "https://stock.naver.com/api/foreign/market/stock/global",
    private val coinRankUrlBase: String = "https://stock.naver.com/api/coin/rank",
) {

    companion object {
        private const val USER_AGENT_KEY = "User-Agent"
        private const val USER_AGENT_VALUE = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private const val ACCEPT_KEY = "Accept"
        private const val ACCEPT_VALUE = "application/json"
        private const val CONTENT_TYPE_KEY = "Content-Type"
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val ORIGIN_KEY = "Origin"
        private const val ORIGIN_VALUE = "https://stock.naver.com"
        private val CHART_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        private val NEWS_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    /**
     * 네트워크 호출이 EDT에서 실행되는 것을 방지합니다.
     *
     * Swing UI 정지를 막기 위한 방어 코드이며, 위반 시 즉시 예외를 발생시킵니다.
     */
    private fun checkBackgroundThread() {
        val app = ApplicationManager.getApplication()

        if (app != null && app.isDispatchThread) {
            logger.error { "Network call on EDT! This will freeze the UI." }
            throw IllegalStateException("Network operations must not be performed on the EDT.")
        }
    }

    /**
     * Naver 의 주식 종목을 검색하는 Client
     */
    fun searchStocks(keyword: String): List<NaverSearchItem> {

        checkBackgroundThread()

        val query = keyword.trim()
        if (query.length < 2) return emptyList()

        return try {
            val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8)
            val target = URLEncoder.encode("stock,index,marketindicator,coin,ipo", Charsets.UTF_8)
            val url = "$searchBaseUrl?query=$encodedQuery&target=$target"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            val jsonString = response.body()
            logger.info { "Response JSON: $jsonString" }

            if (response.statusCode() != 200) {
                logger.error { "Naver search failed. Status: ${response.statusCode()}, URL: $url" }
                return emptyList()
            }

            val wrapper: NaverSearchResponse = objectMapper.readValue(response.body())

            if ((!wrapper.isSuccess && wrapper.result == null) || wrapper.result == null) {
                return emptyList()
            }

            return wrapper.result.items

        } catch (e: Exception) {
            logger.error(e) { "Failed to search stocks for keyword: $keyword" }
            emptyList()
        }
    }

    /**
     * Ticker 에 대한 Naver 실시간 가격 조회 (Facade) 메서드.
     */
    fun fetchStockPrice(tickers: List<Ticker>): List<NaverStockPrice> {
        checkBackgroundThread()

        val stockTickers = tickers.filterNot { it.marketType.isCryptoMarket() }
        if (stockTickers.isEmpty()) {
            return emptyList()
        }

        val (domesticTickers, worldTickers) = stockTickers.partition { it.marketType.isKoreanMarket() }

        logger.debug { "Requesting Domestic: ${domesticTickers.size}, World: ${worldTickers.size}" }

        val domesticResult = fetchPricesInternal(domesticTickers, domesticPriceUrl)
        val worldResult = fetchPricesInternal(worldTickers, worldPriceUrl)

        logger.debug { "Result Domestic: ${domesticResult.datas.size}, World: ${worldResult.datas.size}" }

        return (domesticResult.datas + worldResult.datas)
    }

    /**
     * 코인 실시간 가격 조회 메서드입니다.
     */
    fun fetchCoinPrice(tickers: List<Ticker>): List<NaverCoinPrice> {
        checkBackgroundThread()

        val coinTickers = tickers.filter { it.marketType.isCryptoMarket() }
        if (coinTickers.isEmpty()) return emptyList()

        return try {
            val joined = coinTickers.joinToString(",") { it.tradingSymbol }
            val encoded = URLEncoder.encode(joined, Charsets.UTF_8)
            val fullUrl = "$coinPriceUrl?fqnfTickers=$encoded"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver coin API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            val wrapper: NaverRealtimeCoinPriceResponse = objectMapper.readValue(response.body())
            wrapper.datas
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch coin prices" }
            emptyList()
        }
    }

    /**
     * 코인 상세 시세를 조회합니다.
     */
    fun fetchCoinOverview(exchangeType: String, nfTicker: String): NaverCoinOverview? {
        checkBackgroundThread()

        return try {
            val fullUrl = "$coinOverviewUrl/$exchangeType/$nfTicker"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver coin overview API Error [${response.statusCode()}]: $fullUrl" }
                return null
            }

            objectMapper.readValue<NaverCoinOverview>(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch coin overview: $exchangeType/$nfTicker" }
            null
        }
    }

    /**
     * 코인 일봉 차트 데이터를 조회합니다.
     */
    fun fetchCryptoChartCandles(
        exchangeType: String,
        nfTicker: String,
        marketType: String = "KRW",
        from: LocalDateTime,
        to: LocalDateTime = LocalDateTime.now()
    ): List<NaverCryptoCandle> {
        checkBackgroundThread()

        return try {
            val fromEncoded = URLEncoder.encode(from.toString(), Charsets.UTF_8)
            val toEncoded = URLEncoder.encode(to.toString(), Charsets.UTF_8)
            val fullUrl =
                "$cryptoChartUrl?exchangeType=$exchangeType&nfTicker=$nfTicker&marketType=$marketType&type=days&interval=1&from=$fromEncoded&to=$toEncoded"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver crypto chart API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            val wrapper: NaverCryptoChartResponse = objectMapper.readValue(response.body())
            if (!wrapper.isSuccess) return emptyList()
            wrapper.result
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch crypto chart candles" }
            emptyList()
        }
    }

    /**
     * 국내 뉴스 탭용 카테고리 기사 목록을 조회합니다.
     *
     * `FLASHNEWS`, `MAINNEWS`, `RANKNEWS` 같은 카테고리를 그대로 전달하며,
     * 응답은 목록 UI에서 바로 사용할 수 있도록 평탄한 기사 리스트로 반환합니다.
     */
    fun fetchNewsList(
        category: String = "FLASHNEWS",
        page: Int = 1,
        pageSize: Int = 15,
        date: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    ): List<NaverNewsArticle> {
        checkBackgroundThread()
        return try {
            val normalizedCategory = category.trim().uppercase().ifBlank { "FLASHNEWS" }
            val fullUrl = "$newsListUrl?category=$normalizedCategory&page=${page.coerceAtLeast(1)}" +
                    "&pageSize=${pageSize.coerceIn(1, 50)}&date=${date.format(NEWS_DATE_FORMATTER)}"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver news list API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }
            val body: NaverNewsListResponse = objectMapper.readValue(response.body())
            body.articles
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch news list" }
            emptyList()
        }
    }

    /**
     * 네이버 해외뉴스 메인 리스트를 조회합니다.
     *
     * 원본 응답은 Reuters 기반 별도 DTO를 사용하지만, 반환값은 국내 뉴스와 동일한
     * `NaverNewsArticle` 형식으로 정규화됩니다.
     */
    fun fetchWorldNews(page: Int = 1, pageSize: Int = 15): List<NaverNewsArticle> {
        checkBackgroundThread()
        return try {
            val fullUrl = "$worldNewsUrl?page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 50)}"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver world news API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }
            val body: List<NaverWorldNewsArticle> = objectMapper.readValue(response.body())
            body.map { it.toNewsArticle() }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch world news list" }
            emptyList()
        }
    }

    /**
     * 개별 종목의 국내 관련 뉴스 목록을 조회합니다.
     *
     * 네이버 상세 종목 페이지의 "관련 뉴스" 영역에 대응하는 API이며,
     * 클러스터 응답을 UI에서 바로 쓸 수 있도록 평탄한 기사 리스트로 변환합니다.
     */
    fun fetchDomesticDetailNews(
        itemCode: String,
        page: Int = 1,
        pageSize: Int = 15
    ): List<NaverNewsArticle> {
        checkBackgroundThread()
        val normalizedItemCode = itemCode.trim()
        if (normalizedItemCode.isBlank()) return emptyList()

        return try {
            val encodedItemCode = URLEncoder.encode(normalizedItemCode, Charsets.UTF_8)
            val fullUrl =
                "$domesticDetailNewsUrl?itemCode=$encodedItemCode&page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 50)}"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver domestic detail news API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            val body: NaverDomesticDetailNewsResponse = objectMapper.readValue(response.body())
            body.clusters.flatMap { cluster -> cluster.items.map { it.toNewsArticle() } }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch domestic detail news for itemCode: $itemCode" }
            emptyList()
        }
    }

    /**
     * 국내 종목 상세 개요/핵심 지표를 조회합니다.
     *
     * 하단 뉴스 패널의 국내 종목 개요 카드에서 사용되며, 리서치 기반 fallback보다
     * 우선해서 `PER`, `PBR`, `EPS`, `52주 범위`, 기업 설명을 구성할 때 사용합니다.
     *
     * @param itemCode KRX 종목코드
     * @param codeType 기본값은 `KRX`이며, 네이버 상세 API가 요구하는 코드 구분자입니다.
     * @return 조회 성공 시 상세 DTO, 실패 시 `null`
     */
    fun fetchDomesticStockDetail(itemCode: String, codeType: String = "KRX"): NaverDomesticStockDetail? {
        checkBackgroundThread()
        val normalizedItemCode = itemCode.trim()
        if (normalizedItemCode.isBlank()) return null

        return try {
            val encodedItemCode = URLEncoder.encode(normalizedItemCode, Charsets.UTF_8)
            val encodedCodeType = URLEncoder.encode(codeType.trim().ifBlank { "KRX" }, Charsets.UTF_8)
            val fullUrl = "$domesticStockDetailUrl/$encodedItemCode/detail?codeType=$encodedCodeType"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver domestic stock detail API Error [${response.statusCode()}]: $fullUrl" }
                return null
            }

            objectMapper.readValue<NaverDomesticStockDetail>(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch domestic stock detail for itemCode: $itemCode" }
            null
        }
    }

    /**
     * 개별 해외 종목의 글로벌 뉴스 목록을 조회합니다.
     *
     * Reuters 코드 기반으로 조회하며, 하단 뉴스 패널에서 해외 종목 뉴스 섹션을
     * 구성할 때 사용합니다.
     */
    fun fetchForeignStockNews(
        reutersCode: String,
        page: Int = 1,
        pageSize: Int = 15
    ): List<NaverNewsArticle> {
        checkBackgroundThread()
        val normalizedReutersCode = reutersCode.trim()
        if (normalizedReutersCode.isBlank()) return emptyList()

        return try {
            val encodedReutersCode = URLEncoder.encode(normalizedReutersCode, Charsets.UTF_8)
            val fullUrl =
                "$foreignStockNewsUrl?reutersCode=$encodedReutersCode&page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 50)}"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver foreign stock news API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            val body: List<NaverWorldNewsArticle> = objectMapper.readValue(response.body())
            body.map {
                it.toNewsArticle(
                    badgeLabel = "해외 종목뉴스",
                    sectionKey = "WORLD_STOCK_NEWS",
                    sectionLabel = "해외 종목뉴스"
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch foreign stock news for reutersCode: $reutersCode" }
            emptyList()
        }
    }

    /**
     * 해외 종목의 기업 개요 정보를 조회합니다.
     *
     * 기업 설명, 대표자, 주소, 업종, 시가총액 같은 정적/준정적 정보를 제공하며
     * 하단 뉴스 패널의 개요 카드 본문을 구성할 때 사용합니다.
     */
    fun fetchForeignStockOverview(reutersCode: String): NaverForeignStockOverview? {
        checkBackgroundThread()
        val normalizedReutersCode = reutersCode.trim()
        if (normalizedReutersCode.isBlank()) return null

        return try {
            val encodedReutersCode = URLEncoder.encode(normalizedReutersCode, Charsets.UTF_8)
            val fullUrl = "$foreignStockOverviewUrl/$encodedReutersCode/overview"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver foreign stock overview API Error [${response.statusCode()}]: $fullUrl" }
                return null
            }

            objectMapper.readValue<NaverForeignStockOverview>(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch foreign stock overview for reutersCode: $reutersCode" }
            null
        }
    }

    /**
     * 해외 종목의 기본 시세/밸류에이션 지표를 조회합니다.
     *
     * `PER`, `PBR`, `52주 고저`, 배당수익률처럼 개요 카드에 노출할 핵심 숫자를
     * 구성하는 용도이며, 기업 설명 API와 분리되어 있습니다.
     */
    fun fetchForeignStockBasic(reutersCode: String): NaverForeignStockBasic? {
        checkBackgroundThread()
        val normalizedReutersCode = reutersCode.trim()
        if (normalizedReutersCode.isBlank()) return null

        return try {
            val encodedReutersCode = URLEncoder.encode(normalizedReutersCode, Charsets.UTF_8)
            val fullUrl = "$foreignStockBasicUrl/$encodedReutersCode/basic"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver foreign stock basic API Error [${response.statusCode()}]: $fullUrl" }
                return null
            }

            objectMapper.readValue<NaverForeignStockBasic>(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch foreign stock basic for reutersCode: $reutersCode" }
            null
        }
    }

    /**
     * 키워드 기반 뉴스 검색 결과를 조회합니다.
     *
     * 코인처럼 전용 종목 뉴스 API가 없거나 부족한 경우 보조 뉴스 소스로 사용합니다.
     * query는 내부에서 trim 후 인코딩되며, 빈 문자열이면 즉시 빈 결과를 반환합니다.
     */
    fun fetchNewsSearch(query: String, page: Int = 1, pageSize: Int = 7): List<NaverNewsArticle> {
        checkBackgroundThread()
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()

        return try {
            val encodedQuery = URLEncoder.encode(normalizedQuery, Charsets.UTF_8)
            val fullUrl =
                "$newsSearchUrl?query=$encodedQuery&page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 30)}"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver news search API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            val body: NaverNewsSearchResponse = objectMapper.readValue(response.body())
            if (!body.status.isSuccess) return emptyList()
            body.items.map { it.toNewsArticle() }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch news search results for query: $query" }
            emptyList()
        }
    }

    /**
     * 뉴스 홈 집계 데이터를 조회합니다.
     */
    fun fetchNewsHome(
        flashNewsSize: Int = 4,
        mainNewsSize: Int = 6,
        rankingNewsSize: Int = 5,
        overseasNewsSize: Int = 5,
        focusSize: Int = 5,
        moneyStorySize: Int = 12,
        noticeSize: Int = 5
    ): NaverNewsAggregateResponse? {
        checkBackgroundThread()
        return try {
            val fullUrl =
                "$newsAggregateUrl?flashNewsSize=${flashNewsSize.coerceIn(1, 10)}" +
                        "&mainNewsSize=${mainNewsSize.coerceIn(1, 10)}" +
                        "&rankingNewsSize=${rankingNewsSize.coerceIn(1, 30)}" +
                        "&overseasNewsSize=${overseasNewsSize.coerceIn(1, 10)}" +
                        "&focusSize=${focusSize.coerceIn(1, 10)}" +
                        "&moneyStorySize=${moneyStorySize.coerceIn(1, 20)}" +
                        "&noticeSize=${noticeSize.coerceIn(1, 20)}"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver news aggregate API Error [${response.statusCode()}]: $fullUrl" }
                return null
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch news home" }
            null
        }
    }

    /**
     * 뉴스 홈의 랭킹 기사 목록을 조회합니다.
     */
    fun fetchRankingNews(limit: Int = 20): List<NaverNewsArticle> {
        return fetchNewsHome(rankingNewsSize = limit)?.rankingNews?.map { it.toNewsArticle() } ?: emptyList()
    }

    /**
     * 상단 공지 요약 목록을 조회합니다.
     */
    fun fetchNoticeList(page: Int = 1, pageSize: Int = 10): List<NaverNoticeSummary> {
        checkBackgroundThread()
        return try {
            val fullUrl = "$noticeListUrl?page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 20)}"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver notice list API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch notice list" }
            emptyList()
        }
    }

    /**
     * 국내/해외 주식 차트 캔들 데이터를 조회합니다.
     */
    fun fetchStockChartCandles(
        ticker: Ticker,
        period: PriceHistoryService.Period
    ): List<NaverStockChartCandle> {
        checkBackgroundThread()
        if (ticker.marketType.isCryptoMarket()) return emptyList()

        return try {
            val now = LocalDateTime.now(ticker.marketType.zoneId)
            val from = when (period) {
                PriceHistoryService.Period.DAY -> now.minusYears(1)
                PriceHistoryService.Period.WEEK -> now.minusYears(3)
                PriceHistoryService.Period.MONTH -> now.minusYears(10)
                PriceHistoryService.Period.YEAR -> now.minusYears(40)
            }
            val periodPath = when (period) {
                PriceHistoryService.Period.DAY -> "day"
                PriceHistoryService.Period.WEEK -> "week"
                PriceHistoryService.Period.MONTH -> "month"
                PriceHistoryService.Period.YEAR -> "year"
            }
            val symbol = if (ticker.marketType.isKoreanMarket()) ticker.symbol else ticker.tradingSymbol
            val baseUrl = if (ticker.marketType.isKoreanMarket()) domesticChartUrl else foreignChartUrl
            val fullUrl =
                "$baseUrl/$symbol/$periodPath?startDateTime=${from.format(CHART_TIME_FORMATTER)}&endDateTime=${
                    now.format(
                        CHART_TIME_FORMATTER
                    )
                }"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver stock chart API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch stock chart candles for ${ticker.tradingSymbol}" }
            emptyList()
        }
    }

    /**
     * 국내 지수 풀링 API를 조회합니다.
     */
    fun fetchDomesticIndices(itemCodes: List<String>): NaverMarketIndicatorResponse {
        checkBackgroundThread()
        if (itemCodes.isEmpty()) return NaverMarketIndicatorResponse()

        return try {
            val joined = URLEncoder.encode(itemCodes.joinToString(","), Charsets.UTF_8)
            val fullUrl = "$domesticIndexUrl?itemCodes=$joined"
            getIndicatorResponse(fullUrl)
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch domestic indices" }
            NaverMarketIndicatorResponse()
        }
    }

    /**
     * 해외 지수 풀링 API를 조회합니다.
     */
    fun fetchWorldIndices(reutersCodes: List<String>): NaverMarketIndicatorResponse {
        checkBackgroundThread()
        if (reutersCodes.isEmpty()) return NaverMarketIndicatorResponse()

        return try {
            val joined = URLEncoder.encode(reutersCodes.joinToString(","), Charsets.UTF_8)
            val fullUrl = "$worldIndexUrl?reutersCodes=$joined"
            getIndicatorResponse(fullUrl)
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch world indices" }
            NaverMarketIndicatorResponse()
        }
    }

    /**
     * 원자재(금속/에너지) 지표를 조회합니다.
     */
    fun fetchMarketCommodity(group: String, reutersCode: String): NaverMarketIndicatorResponse {
        checkBackgroundThread()
        return try {
            val baseUrl = when (group.lowercase()) {
                "metals" -> marketMetalUrl
                "energy" -> marketEnergyUrl
                else -> return NaverMarketIndicatorResponse()
            }
            getIndicatorResponse("$baseUrl/$reutersCode")
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch commodity: $group/$reutersCode" }
            NaverMarketIndicatorResponse()
        }
    }

    /**
     * 환전고시 환율 목록을 조회합니다.
     */
    fun fetchExchangeRates(): List<NaverExchangeRateItem> {
        checkBackgroundThread()

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(exchangeRateUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver exchange rate API Error [${response.statusCode()}]: $exchangeRateUrl" }
                return emptyList()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch exchange rates" }
            emptyList()
        }
    }

    fun fetchResearchAggregate(): NaverResearchAggregateResponse {
        checkBackgroundThread()

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(researchAggregateUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(CONTENT_TYPE_KEY, CONTENT_TYPE_JSON)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString("""{"sections":{"researchCategory":true}}"""))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver research aggregate API Error [${response.statusCode()}]: $researchAggregateUrl" }
                return NaverResearchAggregateResponse()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch research aggregate" }
            NaverResearchAggregateResponse()
        }
    }

    fun fetchRecentPopularResearch(): List<NaverResearchArticle> {
        checkBackgroundThread()

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(researchRecentPopularUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver research recent popular API Error [${response.statusCode()}]: $researchRecentPopularUrl" }
                return emptyList()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch recent popular research" }
            emptyList()
        }
    }

    fun fetchCategoryLatestResearch(): NaverResearchLatestResponse {
        checkBackgroundThread()

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(researchCategoryLatestUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver research latest API Error [${response.statusCode()}]: $researchCategoryLatestUrl" }
                return NaverResearchLatestResponse()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch category latest research" }
            NaverResearchLatestResponse()
        }
    }

    fun fetchIndustryResearch(): Map<String, List<NaverResearchArticle>> {
        checkBackgroundThread()

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(industryResearchUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver industry research API Error [${response.statusCode()}]: $industryResearchUrl" }
                return emptyMap()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch industry research" }
            emptyMap()
        }
    }

    fun fetchDiscussionRankings(
        nationType: String = "KOR",
        page: Int = 1,
        size: Int = 20,
        postType: String = "HOT"
    ): NaverDiscussionRankingResponse {
        checkBackgroundThread()

        return try {
            val fullUrl = "$discussionRankingUrl?nationType=$nationType&page=$page&size=$size&postType=$postType"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver discussion ranking API Error [${response.statusCode()}]: $fullUrl" }
                return NaverDiscussionRankingResponse()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch discussion rankings" }
            NaverDiscussionRankingResponse()
        }
    }

    fun fetchResearchRanking(
        rankingType: ResearchRankingType,
        selectedRank: Int
    ): NaverResearchRankingResponse {
        checkBackgroundThread()

        return try {
            val fullUrl = "$researchRankingUrl?rankingType=${rankingType.code}&selectedRank=$selectedRank"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver research ranking API Error [${response.statusCode()}]: $fullUrl" }
                return NaverResearchRankingResponse()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch research ranking: ${rankingType.code}/$selectedRank" }
            NaverResearchRankingResponse()
        }
    }

    /**
     * 네이버 국내 주식 마켓 기본 스크리너 목록을 조회합니다.
     *
     * 실제 국내 stocklist 화면에서 사용하는 엔드포인트입니다.
     */
    fun fetchDomesticMarketStockDefault(
        tradeType: String,
        marketType: String,
        orderType: String,
        startIdx: Int = 0,
        pageSize: Int = 20,
        alertType: String? = null
    ): List<NaverDomesticMarketStockItem> {
        checkBackgroundThread()

        if (orderType.isBlank()) {
            return emptyList()
        }

        return try {
            val query = buildString {
                append("tradeType=")
                append(URLEncoder.encode(tradeType, Charsets.UTF_8))
                append("&marketType=")
                append(URLEncoder.encode(marketType, Charsets.UTF_8))
                append("&orderType=")
                append(URLEncoder.encode(orderType, Charsets.UTF_8))
                append("&startIdx=")
                append(startIdx.coerceAtLeast(0))
                append("&pageSize=")
                append(pageSize.coerceIn(1, 100))

                if (!alertType.isNullOrBlank()) {
                    append("&alertType=")
                    append(URLEncoder.encode(alertType, Charsets.UTF_8))
                }
            }

            val fullUrl = "$domesticMarketStockDefaultUrl?$query"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver domestic market stock default API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to fetch domestic market stock default: tradeType=$tradeType, marketType=$marketType, orderType=$orderType"
            }
            emptyList()
        }
    }

    fun fetchForeignMarketStockGlobal(
        nation: String,
        tradeType: String,
        orderType: String,
        startIdx: Int = 0,
        pageSize: Int = 20
    ): List<NaverForeignMarketStockItem> {
        checkBackgroundThread()

        if (nation.isBlank() || orderType.isBlank()) {
            return emptyList()
        }

        return try {
            val query = buildString {
                append("nation=")
                append(URLEncoder.encode(nation, Charsets.UTF_8))
                append("&tradeType=")
                append(URLEncoder.encode(tradeType, Charsets.UTF_8))
                append("&orderType=")
                append(URLEncoder.encode(orderType, Charsets.UTF_8))
                append("&startIdx=")
                append(startIdx.coerceAtLeast(0))
                append("&pageSize=")
                append(pageSize.coerceIn(1, 100))
            }

            val fullUrl = "$foreignMarketStockGlobalUrl?$query"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver foreign market stock global API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to fetch foreign market stock global: nation=$nation, tradeType=$tradeType, orderType=$orderType"
            }
            emptyList()
        }
    }

    fun fetchCoinRank(
        exchange: String,
        sortType: String,
        page: Int = 1,
        pageSize: Int = 20
    ): NaverCoinRankResponse {
        checkBackgroundThread()

        if (exchange.isBlank() || sortType.isBlank()) {
            return NaverCoinRankResponse()
        }

        return try {
            val query = buildString {
                append("sortType=")
                append(URLEncoder.encode(sortType, Charsets.UTF_8))
                append("&page=")
                append(page.coerceAtLeast(1))
                append("&pageSize=")
                append(pageSize.coerceIn(1, 100))
            }

            val fullUrl = "$coinRankUrlBase/${URLEncoder.encode(exchange, Charsets.UTF_8)}?$query"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .header(ORIGIN_KEY, ORIGIN_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver coin rank API Error [${response.statusCode()}]: $fullUrl" }
                return NaverCoinRankResponse()
            }

            objectMapper.readValue(response.body())
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch coin rank: exchange=$exchange, sortType=$sortType" }
            NaverCoinRankResponse()
        }
    }

    fun fetchStockResearch(itemCode: String, page: Int = 0, size: Int = 10): List<NaverResearchArticle> {
        checkBackgroundThread()
        if (itemCode.isBlank()) return emptyList()

        return try {
            val fullUrl = "$stockResearchBaseUrl/$itemCode/research?page=$page&size=$size"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "Naver stock research API Error [${response.statusCode()}]: $fullUrl" }
                return emptyList()
            }

            objectMapper.readValue<List<NaverStockResearchItem>>(response.body()).map { it.toArticle() }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch stock research for $itemCode" }
            emptyList()
        }
    }

    private fun getIndicatorResponse(fullUrl: String): NaverMarketIndicatorResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(fullUrl))
            .header(USER_AGENT_KEY, USER_AGENT_VALUE)
            .header(ACCEPT_KEY, ACCEPT_VALUE)
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            logger.error { "Naver indicator API Error [${response.statusCode()}]: $fullUrl" }
            return NaverMarketIndicatorResponse()
        }
        return objectMapper.readValue(response.body())
    }

    /**
     * 내부에서 사용될 Naver 가격 조회 메서드.
     */
    private fun fetchPricesInternal(
        tickers: List<Ticker>,
        baseUrl: String
    ): NaverRealTimeStockPriceResponse {

        if (tickers.isEmpty()) {
            return NaverRealTimeStockPriceResponse()
        }

        return try {
            val joinedCodes = tickers.joinToString(",") { it.tradingSymbol }
            val encodedCodes = URLEncoder.encode(joinedCodes, Charsets.UTF_8)
            val fullUrl = "$baseUrl/$encodedCodes"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header(USER_AGENT_KEY, USER_AGENT_VALUE)
                .header(ACCEPT_KEY, ACCEPT_VALUE)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                logger.error { "Naver API Error [${response.statusCode()}]: $fullUrl" }
                return NaverRealTimeStockPriceResponse()
            }

            return objectMapper.readValue(response.body())

        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch stock prices $e " }
            return NaverRealTimeStockPriceResponse()
        }
    }


}
