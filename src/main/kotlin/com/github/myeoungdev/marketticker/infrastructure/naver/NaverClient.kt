package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.application.service.PriceHistoryService
import com.github.myeoungdev.marketticker.common.config.httpClient
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.Ticker
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
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-11-30
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
    private val domesticChartUrl: String = "https://api.stock.naver.com/chart/domestic/item",
    private val foreignChartUrl: String = "https://api.stock.naver.com/chart/foreign/item",
    private val newsListUrl: String = "https://stock.naver.com/api/domestic/news/list",
    private val worldNewsUrl: String = "https://stock.naver.com/api/foreign/news/worldNews",
    private val newsAggregateUrl: String = "https://stock.naver.com/api/domestic/news/aggregate/home",
    private val noticeListUrl: String = "https://stock.naver.com/api/domestic/home/noticeList"
) {

    companion object {
        private const val USER_AGENT_KEY = "User-Agent"
        private const val USER_AGENT_VALUE = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private const val ACCEPT_KEY = "Accept"
        private const val ACCEPT_VALUE = "application/json"
        private const val ORIGIN_KEY = "Origin"
        private const val ORIGIN_VALUE = "https://stock.naver.com"
        private val CHART_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        private val NEWS_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    /**
     * EDT (UI 쓰레드) 에사 호출 시 강제 예외 발생 메서드 입니다.
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
     * 국내 뉴스 리스트를 조회합니다.
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
     * 해외 뉴스 리스트를 조회합니다.
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
