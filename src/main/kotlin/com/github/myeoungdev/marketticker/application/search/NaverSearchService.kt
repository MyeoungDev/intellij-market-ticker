package com.github.myeoungdev.marketticker.application.search

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchResultPayload
import java.net.http.HttpClient

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-11-30
 */
class NaverSearchService(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .build()
) {

    private val mapper = jacksonObjectMapper()

    /**
     * 네이버 자동완성 API를 호출해서 종목 검색 결과를 가져온다.
     * - 최소 2글자 이상일 때만 호출
     * - 에러/예외 발생 시 빈 리스트 반환
     */
    fun searchStocks(keyword: String): List<NaverSearchResultPayload> {
        if (keyword.length < 2) return emptyList()

        return emptyList();
//        return try {
//            val encoded = URLEncoder.encode(keyword, Charsets.UTF_8)
//            val url =
//                "https://m.stock.naver.com/front-api/search/autoComplete?query=$encoded&target=stock"
//
//            val request = HttpRequest.newBuilder()
//                .uri(URI.create(url))
////                .header(
////                    "User-Agent",
////                    "Mozilla/5.0 (compatible; IntelliJ-Market-Ticker/1.0)"
////                )
//                .header("Accept", "application/json, text/plain, */*")
////                .header("Referer", "https://m.stock.naver.com/search")
//                .GET()
//                .build()
//
//            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
//
//            if (response.statusCode() != 200) {
//                println("Naver search error: status=${response.statusCode()}")
//                return emptyList()
//            }
//
//            val body = response.body()
//
//            val root: JsonNode = mapper.readTree(body)
//            parseResultsFromJson(root)
//            return NaverSearchResultPayload()
//        } catch (e: Exception) {
//            println("Naver search exception: ${e.javaClass.simpleName}: ${e.message}")
//            emptyList()
//        }
    }
}
