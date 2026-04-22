package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenerPreset
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

class NaverScreenerProviderMarketTest {

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
    fun `미국 주식 스크리너 응답을 행으로 변환한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/foreign/market/stock/global"))
                .withQueryParam("nation", equalTo("USA"))
                .withQueryParam("tradeType", equalTo("ALL"))
                .withQueryParam("orderType", equalTo("marketValue"))
                .withQueryParam("startIdx", equalTo("0"))
                .withQueryParam("pageSize", equalTo("10"))
                .willReturn(okJson(NaverFixtures.JSON_FOREIGN_MARKET_STOCK_GLOBAL_SUCCESS))
        )

        val result = provider.getScreen(MarketType.USA, ScreenerPreset.MARKET_CAP, 10)

        assertThat(result).isNotEmpty
        assertThat(result.first().ticker.symbol).isEqualTo("NVDA.O")
        assertThat(result.first().ticker.tradingSymbol).isEqualTo("NVDA")
        assertThat(result.first().ticker.marketType).isEqualTo(MarketType.NASDAQ)
        assertThat(result.first().marketCap).isEqualTo("4775193000000.0000")
    }

    @Test
    fun `코인 스크리너 응답을 행으로 변환한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/coin/rank/UPBIT"))
                .withQueryParam("sortType", equalTo("marketValue"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("pageSize", equalTo("10"))
                .willReturn(okJson(NaverFixtures.JSON_COIN_RANK_SUCCESS))
        )

        val result = provider.getScreen(MarketType.UPBIT, ScreenerPreset.MARKET_CAP, 10)

        assertThat(result).isNotEmpty
        assertThat(result.first().ticker.symbol).isEqualTo("BTC")
        assertThat(result.first().ticker.marketType).isEqualTo(MarketType.UPBIT)
        assertThat(result.first().volume).isEqualTo("1777.711648")
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
            domesticMarketStockDefaultUrl = "$baseUrl/domestic/market/stock/default",
            foreignMarketStockGlobalUrl = "$baseUrl/foreign/market/stock/global",
            coinRankUrlBase = "$baseUrl/coin/rank"
        )
    }
}
