package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
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
class NaverClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) {

    val mapper = jacksonObjectMapper()

    inline fun <reified T> parseResponse(json: String): NaverResponse<T> {
        return mapper.readValue(json)
    }

    fun searchStocks(keyword: String): List<NaverSearchItem> {
        if (keyword.length < 2) return emptyList()

        val encoded = URLEncoder.encode(keyword, Charsets.UTF_8)
        val url =
            "https://m.stock.naver.com/front-api/search/autoComplete?query=$encoded&target=stock"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .header("Accept", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            println("Naver search error: ${response.statusCode()}")
            return emptyList()
        }

        val body = response.body()
        val wrapper: NaverResponse<NaverSearchResultPayload> = parseResponse(body)

        if (!wrapper.isSuccess || wrapper.result == null) {
            return emptyList()
        }

        return wrapper.result.items
    }

    fun getPrice(tickers: List<Ticker>): List<TickerPrice> {

        if (tickers.isEmpty()) {
            return emptyList()
        }

        val encoded = URLEncoder.encode(tickers.toString(), Charsets.UTF_8)
        val url =
            "https://polling.finance.naver.com/api/realtime/domestic/stock/${encoded}"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .header("Accept", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            println("Naver search error: ${response.statusCode()}")
            return emptyList()
        }

        val body = response.body()

        return emptyList()
    }
}