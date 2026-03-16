package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.fixtures.naver.NaverFixtures
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Duration

@DisplayName("NaverNewsProvider 테스트")
class NaverNewsProviderTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var provider: NaverNewsProvider

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMockServer.start()

        provider = NaverNewsProvider(createClient(wireMockServer.baseUrl()))
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `뉴스 홈 데이터를 application 모델로 조합한다`() {
        stubNewsHome()

        val result = provider.getHeadlineNews()

        assertThat(result.headlines["FLASHNEWS"]).isNotEmpty
        assertThat(result.headlines["MAINNEWS"]).isNotEmpty
        assertThat(result.headlines["WORLDNEWS"]).isNotEmpty
        assertThat(result.worldNews).isNotEmpty
        assertThat(result.moneyStories).isNotEmpty
        assertThat(result.focusSections).isNotEmpty
    }

    @Test
    fun `해외 종목 뉴스는 개요 카드와 병합 뉴스로 구성한다`() {
        stubForeignTicker()

        val result = provider.getTickerNews(
            Ticker("NVDA", "NVDA.O", "엔비디아", MarketType.NASDAQ, "USA", "미국")
        )

        assertThat(result.overviewCard).isNotNull
        assertThat(result.overviewCard?.title).contains("엔비디아")
        assertThat(result.overviewCard?.primaryMetrics).contains("PER", "PBR")
        assertThat(result.articles).isNotEmpty
        assertThat(result.articles.first().url).isNotBlank()
    }

    @Test
    fun `국내 종목 뉴스는 상세 개요와 국내 뉴스로 구성한다`() {
        stubDomesticTicker()

        val result = provider.getTickerNews(
            Ticker("003280", "003280", "흥아해운", MarketType.KOSPI, "KOR", "대한민국")
        )

        assertThat(result.overviewCard).isNotNull
        assertThat(result.overviewCard?.title).isEqualTo("흥아해운")
        assertThat(result.overviewCard?.primaryMetrics).contains("PER", "PBR", "EPS")
        assertThat(result.articles).isNotEmpty
    }

    @Test
    fun `코인 뉴스는 코인 개요와 검색 뉴스로 구성한다`() {
        stubCryptoTicker()

        val result = provider.getTickerNews(
            Ticker("BTC", "BTC_KRW_UPBIT", "비트코인", MarketType.UPBIT, "KOR", "대한민국")
        )

        assertThat(result.overviewCard).isNotNull
        assertThat(result.overviewCard?.title).contains("비트코인")
        assertThat(result.overviewCard?.primaryMetrics).contains("김프")
        assertThat(result.articles).isNotEmpty
        assertThat(result.articles.map { it.title + it.summary }).anySatisfy { combined ->
            assertThat(combined).contains("비트코인")
        }
    }

    private fun stubNewsHome() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/aggregate/home"))
                .willReturn(okJson(NaverFixtures.JSON_NEWS_HOME_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/list"))
                .withQueryParam("category", equalTo("FLASHNEWS"))
                .willReturn(okJson(NaverFixtures.JSON_NEWS_LIST_FLASH_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/list"))
                .withQueryParam("category", equalTo("MAINNEWS"))
                .willReturn(okJson(NaverFixtures.JSON_NEWS_LIST_FLASH_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/foreign/news/worldNews"))
                .willReturn(okJson(NaverFixtures.JSON_WORLD_NEWS_SUCCESS))
        )
    }

    private fun stubForeignTicker() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/securityService/stock/NVDA.O/overview"))
                .willReturn(okJson(NaverFixtures.JSON_FOREIGN_STOCK_OVERVIEW_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/securityService/stock/NVDA.O/basic"))
                .willReturn(okJson(NaverFixtures.JSON_FOREIGN_STOCK_BASIC_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/detail/news"))
                .withQueryParam("itemCode", equalTo("NVDA.O"))
                .willReturn(okJson(NaverFixtures.JSON_DOMESTIC_DETAIL_NEWS_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/foreign/worldStock/list"))
                .withQueryParam("reutersCode", equalTo("NVDA.O"))
                .willReturn(okJson(NaverFixtures.JSON_FOREIGN_STOCK_NEWS_SUCCESS))
        )
    }

    private fun stubDomesticTicker() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/detail/003280/detail"))
                .withQueryParam("codeType", equalTo("KRX"))
                .willReturn(okJson(NaverFixtures.JSON_DOMESTIC_STOCK_DETAIL_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/detail/news"))
                .withQueryParam("itemCode", equalTo("003280"))
                .willReturn(okJson(NaverFixtures.JSON_DOMESTIC_DETAIL_NEWS_SUCCESS))
        )
    }

    private fun stubCryptoTicker() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/api/coin/price/UPBIT/BTC"))
                .willReturn(okJson(NaverFixtures.JSON_COIN_OVERVIEW_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/search"))
                .withQueryParam("query", matching(".+"))
                .willReturn(okJson(NaverFixtures.JSON_NEWS_SEARCH_CRYPTO_SUCCESS))
        )
    }

    private fun createClient(baseUrl: String): NaverClient {
        return NaverClient(
            client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build(),
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
