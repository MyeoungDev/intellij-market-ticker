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
    fun `네이버 랭킹 응답을 스크리너 행으로 변환한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/research/ranking"))
                .withQueryParam("rankingType", equalTo("SEARCH_TOP"))
                .withQueryParam("selectedRank", equalTo("1"))
                .willReturn(okJson(NaverFixtures.JSON_RESEARCH_RANKING_SEARCH_TOP_SUCCESS))
        )

        val result = provider.getScreen(ScreenerPreset.SEARCH_TOP, limit = 10)

        assertThat(result).isNotEmpty
        assertThat(result.first().ticker.symbol).isEqualTo("005930")
        assertThat(result.first().ticker.marketType).isIn(MarketType.KOSPI, MarketType.UNKNOWN)
        assertThat(result.first().marketCap).isNotBlank()
        assertThat(result.first().price).isNotBlank()
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
            newsSearchUrl = "$baseUrl/news/search"
        )
    }
}
