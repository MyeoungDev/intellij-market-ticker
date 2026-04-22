package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenerPreset
import com.github.myeoungdev.marketticker.fixtures.naver.NaverFixtures
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Duration

class NaverScreenerProviderTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var provider: NaverScreenerProvider

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMockServer.start()
        provider = NaverScreenerProvider(createClient(wireMockServer.baseUrl()))
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `네이버 마켓 스크리너 응답을 스크리너 행으로 변환한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/market/stock/default"))
                .withQueryParam("tradeType", equalTo("KRX"))
                .withQueryParam("marketType", equalTo("ALL"))
                .withQueryParam("orderType", equalTo("searchTop"))
                .withQueryParam("startIdx", equalTo("0"))
                .withQueryParam("pageSize", equalTo("10"))
                .willReturn(okJson(NaverFixtures.JSON_DOMESTIC_MARKET_STOCK_DEFAULT_SUCCESS))
        )

        val result = provider.getScreen(MarketType.KOREA, ScreenerPreset.SEARCH_TOP, limit = 10)

        assertThat(result).isNotEmpty
        assertThat(result.first().ticker.symbol).isEqualTo("005930")
        assertThat(result.first().ticker.marketType).isIn(MarketType.KOSPI, MarketType.UNKNOWN)
        assertThat(result.first().marketCap).isNotBlank()
        assertThat(result.first().price).isNotBlank()
    }

    @Test
    fun `네이버 랭킹 응답에 null 문자열 필드가 있어도 스크리너를 반환한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/research/ranking"))
                .withQueryParam("rankingType", equalTo("SEARCH_TOP"))
                .withQueryParam("selectedRank", equalTo("1"))
                .willReturn(
                    okJson(
                        """
                        {
                          "ranking": [
                            {
                              "itemname": "테스트",
                              "itemcode": "000001",
                              "sosok": "0",
                              "marketStatus": "CLOSE",
                              "nowPrice": "1000",
                              "prevChangeRate": "1.23",
                              "per": null,
                              "pbr": null,
                              "dividendRate": null,
                              "marketSum": null,
                              "tradeVolume": "12345",
                              "tradeAmount": null,
                              "eps": null,
                              "roe": null,
                              "roa": null,
                              "listedDate": null,
                              "week52HighPrice": null,
                              "week52LowPrice": null,
                              "prevChangePrice": null,
                              "listedStockCnt": null,
                              "frgnHoldRate": null,
                              "salesIncreasingRate": null,
                              "operatingProfitIncreasingRate": null,
                              "netIncome": null,
                              "sales": null,
                              "dividend": null
                            }
                          ],
                          "latestResearch": []
                        }
                        """.trimIndent()
                    )
                )
        )

        val result = provider.getScreen(ScreenerPreset.SEARCH_TOP, limit = 10)

        assertThat(result).hasSize(1)
        assertThat(result.first().ticker.symbol).isEqualTo("000001")
        assertThat(result.first().pe).isEmpty()
        assertThat(result.first().marketCap).isEmpty()
        assertThat(result.first().volume).isEqualTo("12345")
    }

    @Test
    fun `스크리너는 여러 selectedRank 페이지를 합쳐 limit 만큼 반환한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/research/ranking"))
                .withQueryParam("rankingType", equalTo("SEARCH_TOP"))
                .withQueryParam("selectedRank", equalTo("1"))
                .willReturn(
                    okJson(
                        """
                        {
                          "ranking": [
                            {"itemname":"A","itemcode":"000001","sosok":"0","marketStatus":"CLOSE","nowPrice":"100","prevChangeRate":"1","tradeVolume":"10"},
                            {"itemname":"B","itemcode":"000002","sosok":"0","marketStatus":"CLOSE","nowPrice":"200","prevChangeRate":"2","tradeVolume":"20"},
                            {"itemname":"C","itemcode":"000003","sosok":"1","marketStatus":"CLOSE","nowPrice":"300","prevChangeRate":"3","tradeVolume":"30"},
                            {"itemname":"D","itemcode":"000004","sosok":"1","marketStatus":"CLOSE","nowPrice":"400","prevChangeRate":"4","tradeVolume":"40"},
                            {"itemname":"E","itemcode":"000005","sosok":"1","marketStatus":"CLOSE","nowPrice":"500","prevChangeRate":"5","tradeVolume":"50"}
                          ],
                          "latestResearch": []
                        }
                        """.trimIndent()
                    )
                )
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/research/ranking"))
                .withQueryParam("rankingType", equalTo("SEARCH_TOP"))
                .withQueryParam("selectedRank", equalTo("2"))
                .willReturn(
                    okJson(
                        """
                        {
                          "ranking": [
                            {"itemname":"F","itemcode":"000006","sosok":"0","marketStatus":"CLOSE","nowPrice":"600","prevChangeRate":"6","tradeVolume":"60"},
                            {"itemname":"G","itemcode":"000007","sosok":"0","marketStatus":"CLOSE","nowPrice":"700","prevChangeRate":"7","tradeVolume":"70"}
                          ],
                          "latestResearch": []
                        }
                        """.trimIndent()
                    )
                )
        )

        val result = provider.getScreen(ScreenerPreset.SEARCH_TOP, limit = 7)

        assertThat(result).hasSize(7)
        assertThat(result.map { it.ticker.symbol }).containsExactly(
            "000001", "000002", "000003", "000004", "000005", "000006", "000007"
        )
    }

    private fun createClient(baseUrl: String): NaverClient {
        return NaverClient(
            client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build(),
            searchBaseUrl = "$baseUrl/search",
            domesticPriceUrl = "$baseUrl/domestic/stock",
            worldPriceUrl = "$baseUrl/worldstock/stock",
            coinPriceUrl = "$baseUrl/coin/price",
            coinOverviewUrl = "$baseUrl/api/coin/price",
            cryptoChartUrl = "$baseUrl/chart/cryptoChartData",
            domesticIndexUrl = "$baseUrl/domestic/index",
            worldIndexUrl = "$baseUrl/worldstock/index",
            marketMetalUrl = "$baseUrl/marketindex/metals",
            marketEnergyUrl = "$baseUrl/marketindex/energy",
            domesticChartUrl = "$baseUrl/chart/domestic/item",
            foreignChartUrl = "$baseUrl/chart/foreign/item",
            researchAggregateUrl = "$baseUrl/research/aggregate",
            researchRecentPopularUrl = "$baseUrl/research/recent-popular",
            researchCategoryLatestUrl = "$baseUrl/research/category-latest",
            industryResearchUrl = "$baseUrl/research/industry-research",
            discussionRankingUrl = "$baseUrl/community/discussion/rankings",
            researchRankingUrl = "$baseUrl/research/ranking",
            stockResearchBaseUrl = "$baseUrl/research",
            newsListUrl = "$baseUrl/news/list",
            worldNewsUrl = "$baseUrl/foreign/news/worldNews",
            domesticDetailNewsUrl = "$baseUrl/domestic/detail/news",
            domesticStockDetailUrl = "$baseUrl/domestic/detail",
            foreignStockNewsUrl = "$baseUrl/foreign/worldStock/list",
            foreignStockOverviewUrl = "$baseUrl/securityService/stock",
            foreignStockBasicUrl = "$baseUrl/securityService/stock",
            newsAggregateUrl = "$baseUrl/news/aggregate/home",
            noticeListUrl = "$baseUrl/home/noticeList",
            newsSearchUrl = "$baseUrl/news/search",
            domesticMarketStockDefaultUrl = "$baseUrl/domestic/market/stock/default"
        )
    }
}
