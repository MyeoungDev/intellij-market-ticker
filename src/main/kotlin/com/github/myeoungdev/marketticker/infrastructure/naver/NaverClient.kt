package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.common.config.httpClient
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverRealTimeStockPriceResponse
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

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-11-30
 */
private val logger = KotlinLogging.logger {}

class NaverClient(
    private val client: HttpClient = httpClient,
    private val searchBaseUrl: String = "https://m.stock.naver.com/front-api/search/autoComplete",
    private val domesticPriceUrl: String = "https://polling.finance.naver.com/api/realtime/domestic/stock",
    private val worldPriceUrl: String = "https://polling.finance.naver.com/api/realtime/worldstock/stock"
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
            val url = "$searchBaseUrl?query=$encodedQuery&target=stock"

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

            if (!wrapper.isSuccess || wrapper.result == null) {
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

        if (tickers.isEmpty()) {
            return emptyList()
        }

        val (domesticTickers, worldTickers) = tickers.partition { it.marketType.isKoreanMarket() }

        logger.debug { "Requesting Domestic: ${domesticTickers.size}, World: ${worldTickers.size}" }

        val domesticResult = fetchPricesInternal(domesticTickers, domesticPriceUrl)
        val worldResult = fetchPricesInternal(worldTickers, worldPriceUrl)

        logger.debug { "Result Domestic: ${domesticResult.datas.size}, World: ${worldResult.datas.size}" }

        return (domesticResult.datas + worldResult.datas)
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