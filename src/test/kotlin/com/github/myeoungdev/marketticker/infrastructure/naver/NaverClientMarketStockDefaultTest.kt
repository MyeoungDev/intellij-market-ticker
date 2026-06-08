package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.fixtures.naver.NaverFixtures
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Duration

class NaverClientMarketStockDefaultTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var naverClient: NaverClient

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMockServer.start()
        naverClient = createClient(wireMockServer.baseUrl())
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `국내 마켓 스크리너 기본 API 응답을 파싱한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/market/stock/default"))
                .withQueryParam("tradeType", equalTo("KRX"))
                .withQueryParam("marketType", equalTo("ALL"))
                .withQueryParam("orderType", equalTo("searchTop"))
                .withQueryParam("startIdx", equalTo("0"))
                .withQueryParam("pageSize", equalTo("3"))
                .willReturn(okJson(NaverFixtures.JSON_DOMESTIC_MARKET_STOCK_DEFAULT_SUCCESS))
        )

        val result = naverClient.fetchDomesticMarketStockDefault(
            tradeType = "KRX",
            marketType = "ALL",
            orderType = "searchTop",
            startIdx = 0,
            pageSize = 3
        )

        assertThat(result).hasSize(3)
        assertThat(result.first().itemcode).isEqualTo("005930")
        assertThat(result.first().itemname).isEqualTo("삼성전자")
        assertThat(result.first().nowPrice).isEqualTo("213750")
        assertThat(result.first().marketSum).isEqualTo("1249642052000000")
    }

    @Test
    fun `국내 마켓 스크리너는 명시적 null 선택 필드를 허용한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/market/stock/default"))
                .withQueryParam("tradeType", equalTo("KRX"))
                .withQueryParam("marketType", equalTo("ALL"))
                .withQueryParam("orderType", equalTo("searchTop"))
                .withQueryParam("startIdx", equalTo("0"))
                .withQueryParam("pageSize", equalTo("1"))
                .willReturn(
                    okJson(
                        """
                        [
                          {
                            "itemname": "테스트",
                            "itemcode": "000001",
                            "sosok": "0",
                            "marketStatus": "OPEN",
                            "nowPrice": "1000",
                            "prevChangeRate": null,
                            "tradeVolume": null,
                            "marketSum": null,
                            "per": null
                          }
                        ]
                        """.trimIndent()
                    )
                )
        )

        val result = naverClient.fetchDomesticMarketStockDefault(
            tradeType = "KRX",
            marketType = "ALL",
            orderType = "searchTop",
            startIdx = 0,
            pageSize = 1
        )

        assertThat(result).hasSize(1)
        assertThat(result.first().prevChangeRate).isNull()
        assertThat(result.first().marketSum).isNull()
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
