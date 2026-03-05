package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.common.config.httpClient
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCoinPrice
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCryptoCandle
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCryptoChartResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverRealtimeCoinPriceResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverRealTimeStockPriceResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverMarketIndicatorResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverSearchItem
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverSearchResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverStockPrice
import com.intellij.openapi.application.ApplicationManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime

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
    private val cryptoChartUrl: String = "https://m.stock.naver.com/front-api/chart/cryptoChartData",
    private val domesticIndexUrl: String = "https://stock.naver.com/api/polling/domestic/index",
    private val worldIndexUrl: String = "https://stock.naver.com/api/polling/worldstock/index",
    private val marketMetalUrl: String = "https://stock.naver.com/api/polling/marketindex/metals",
    private val marketEnergyUrl: String = "https://stock.naver.com/api/polling/marketindex/energy"
) {

    companion object {
        private const val USER_AGENT_KEY = "User-Agent"
        private const val USER_AGENT_VALUE = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private const val ACCEPT_KEY = "Accept"
        private const val ACCEPT_VALUE = "application/json"
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
     * 코인 일봉 차트 데이터를 조회합니다.
     */
    fun fetchCryptoChartCandles(
        exchangeType: String,
        nfTicker: String,
        marketType: String = "KRW",
        from: LocalDateTime
    ): List<NaverCryptoCandle> {
        checkBackgroundThread()

        return try {
            val fromEncoded = URLEncoder.encode(from.toString(), Charsets.UTF_8)
            val fullUrl =
                "$cryptoChartUrl?exchangeType=$exchangeType&nfTicker=$nfTicker&marketType=$marketType&type=days&interval=1&from=$fromEncoded"

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
