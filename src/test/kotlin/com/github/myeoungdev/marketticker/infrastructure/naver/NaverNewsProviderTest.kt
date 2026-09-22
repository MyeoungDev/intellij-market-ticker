package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.news.NewsLoadStatus
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

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
        stubCoreNews()

        val result = provider.getHeadlineNews()

        assertThat(result.headlines["FLASHNEWS"]).isNotEmpty
        assertThat(result.headlines["MAINNEWS"]).isNotEmpty
        assertThat(result.headlines["WORLDNEWS"]).isNotEmpty
        assertThat(result.worldNews).isNotEmpty
        assertThat(result.moneyStories).isNotEmpty
        assertThat(result.focusSections).hasSize(6)
        assertThat(result.notices).isNotEmpty
        assertThat(result.categoryStates.values.map { it.status }).containsOnly(NewsLoadStatus.SUCCESS)
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/news/aggregate/home")))
    }

    @Test
    fun `뉴스 홈의 혼합 시간 포맷을 KST 기준 yyyy-MM-dd HHmm 으로 정규화한다`() {
        stubCoreNews()

        val fixedProvider = NaverNewsProvider(
            createClient(wireMockServer.baseUrl()),
            Clock.fixed(Instant.parse("2026-03-09T14:00:00Z"), ZoneOffset.UTC)
        )

        val result = fixedProvider.getHeadlineNews(pageSize = 15)

        assertThat(result.headlines["FLASHNEWS"]?.first()?.publishedAt).isEqualTo("2026-03-06 09:43")
        assertThat(result.worldNews.first().title).isEqualTo("우크라이나의 전력 수입이 원자력 발전소 수리로 증가한다고 ExPro 컨설팅은 말합니다.")
        assertThat(result.worldNews.first().publishedAt).isEqualTo("2026-03-10 00:10")
        assertThat(result.worldNews.first().sectionLabel).isEqualTo("해외 뉴스")
    }

    @Test
    fun `뉴스 홈은 일부 카테고리 실패에도 성공 카테고리를 유지한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/list"))
                .withQueryParam("category", equalTo("FLASHNEWS"))
                .willReturn(okJson(NaverFixtures.JSON_NEWS_LIST_FLASH_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/list"))
                .withQueryParam("category", equalTo("MAINNEWS"))
                .willReturn(serverError())
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/foreign/news/worldNews"))
                .willReturn(okJson(NaverFixtures.JSON_WORLD_NEWS_SUCCESS))
        )

        val result = provider.getHeadlineNews(pageSize = 15)

        assertThat(result.headlines["FLASHNEWS"]).isNotEmpty
        assertThat(result.headlines["WORLDNEWS"]).isNotEmpty
        assertThat(result.headlines["MAINNEWS"]).isEmpty()
        assertThat(result.categoryStates["MAINNEWS"]?.status).isEqualTo(NewsLoadStatus.FAILED)
        assertThat(result.categoryStates["FLASHNEWS"]?.status).isEqualTo(NewsLoadStatus.SUCCESS)
    }

    @Test
    fun `뉴스 포커스 한 섹션 실패에도 다른 포커스와 기본 뉴스는 유지한다`() {
        stubCoreNews()
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/focus"))
                .withQueryParam("sid", equalTo("401"))
                .willReturn(serverError())
        )

        val result = provider.getHeadlineNews(pageSize = 15)

        assertThat(result.headlines["MAINNEWS"]).isNotEmpty
        assertThat(result.focusSections).hasSize(5)
        assertThat(result.categoryStates["FOCUS_401"]?.status).isEqualTo(NewsLoadStatus.EMPTY)
        assertThat(result.categoryStates["FOCUS_402"]?.status).isEqualTo(NewsLoadStatus.SUCCESS)
    }

    @Test
    fun `뉴스 카테고리의 다음 페이지를 조회한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/list"))
                .withQueryParam("category", equalTo("FLASHNEWS"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(
                    okJson(
                        """
                        {
                          "articles": [
                            {
                              "officeId": "777",
                              "officeHname": "테스트일보",
                              "articleId": "0000000002",
                              "title": "두 번째 페이지 기사",
                              "datetime": "2026-03-06 10:43:54",
                              "type": "1",
                              "subcontent": "페이지 2 본문",
                              "thumbUrl": "https://imgnews.pstatic.net/test2.jpg"
                            }
                          ],
                          "date": "20260306",
                          "isFirstDate": false
                        }
                        """.trimIndent()
                    )
                )
        )

        val result = provider.getCategoryNews("FLASHNEWS", page = 2, pageSize = 15)

        assertThat(result).hasSize(1)
        assertThat(result.first().title).isEqualTo("두 번째 페이지 기사")
        assertThat(result.first().publishedAt).isEqualTo("2026-03-06 10:43")
    }

    @Test
    fun `ticker 뉴스는 publishedAtInstant 기준으로 정렬한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/detail/003280/detail"))
                .willReturn(okJson(NaverFixtures.JSON_DOMESTIC_STOCK_DETAIL_SUCCESS))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/detail/news"))
                .withQueryParam("itemCode", equalTo("003280"))
                .willReturn(
                    okJson(
                        """
                        {
                          "total": "2",
                          "clusters": [
                            {
                              "itemTotal": "2",
                              "items": [
                                {
                                  "id": "a1",
                                  "officeId": "001",
                                  "articleId": "0000000001",
                                  "officeName": "연합뉴스",
                                  "datetime": "2026-03-09T13:28:08.000Z",
                                  "title": "더 이른 기사",
                                  "body": "본문 A"
                                },
                                {
                                  "id": "a2",
                                  "officeId": "002",
                                  "articleId": "0000000002",
                                  "officeName": "한국경제",
                                  "datetime": "1시간 전",
                                  "title": "더 최근 기사",
                                  "body": "본문 B"
                                }
                              ]
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                )
        )
        val fixedProvider = NaverNewsProvider(
            createClient(wireMockServer.baseUrl()),
            Clock.fixed(Instant.parse("2026-03-09T14:30:00Z"), ZoneOffset.UTC)
        )

        val result = fixedProvider.getTickerNews(
            Ticker("003280", "003280", "흥아해운", MarketType.KOSPI, "KOR", "대한민국")
        )

        assertThat(result.articles).hasSize(2)
        assertThat(result.articles.map { it.publishedAt }).containsExactly(
            "2026-03-09 22:30",
            "2026-03-09 22:28"
        )
        assertThat(result.articles.first().publishedAtInstant).isAfter(result.articles.last().publishedAtInstant)
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/research/003280/research")))
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
        assertThat(result.overviewCard?.primaryMetrics).contains("현재가", "등락률", "PER", "PBR")
        assertThat(result.overviewCard?.secondaryMetrics).contains("EPS")
        assertThat(result.articles).isNotEmpty
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/research/003280/research")))
    }

    @Test
    fun `국내 종목 상세가 없어도 뉴스 로딩은 리서치 API를 호출하지 않는다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/detail/003280/detail"))
                .willReturn(notFound())
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/domestic/detail/news"))
                .withQueryParam("itemCode", equalTo("003280"))
                .willReturn(okJson(NaverFixtures.JSON_DOMESTIC_DETAIL_NEWS_SUCCESS))
        )

        val result = provider.getTickerNews(
            Ticker("003280", "003280", "흥아해운", MarketType.KOSPI, "KOR", "대한민국")
        )

        assertThat(result.overviewCard?.summary).isEqualTo("국내 종목 개요 정보가 제한되어 있습니다.")
        assertThat(result.articles).isNotEmpty
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/research/003280/research")))
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
        assertThat(result.articles.first().publishedAt).isEqualTo("2026-03-14 11:00")
        assertThat(result.articles.map { it.title + it.summary }).anySatisfy { combined ->
            assertThat(combined).contains("비트코인")
        }
    }

    private fun stubCoreNews() {
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
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/focus"))
                .willReturn(okJson(focusResponse()))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/content/moneyStory"))
                .willReturn(okJson(moneyStoryResponse()))
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/news/noticeList"))
                .willReturn(okJson(noticeResponse()))
        )
    }

    private fun focusResponse(): String = """
        {
          "page": 1,
          "pageSize": 5,
          "articleTotal": 1,
          "recentdates": ["20260309"],
          "date": "20260309",
          "isFirstDate": true,
          "articles": [{
            "officeID": "001",
            "officeHName": "테스트언론",
            "articleID": "0000000010",
            "title": "포커스 기사",
            "subcontent": "포커스 요약",
            "date": "20260309110000",
            "thumbnails": {"thumbnail154": "https://img.example/focus.jpg"},
            "url": "https://n.news.naver.com/article/001/0000000010"
          }]
        }
    """.trimIndent()

    private fun moneyStoryResponse(): String = """
        {
          "totalCount": 1,
          "moneyContentList": [{
            "id": 1,
            "category": {"mainName": "투자인사이트", "subName": "전략", "imageUrl": "https://img.example/story.jpg"},
            "title": "머니스토리 기사",
            "imageUrl": "https://img.example/story.jpg",
            "teaser": "스토리 요약",
            "displayAt": "2026-03-09T11:00:00",
            "buttonUrl": "https://m.stock.naver.com/money/stories/1"
          }]
        }
    """.trimIndent()

    private fun noticeResponse(): String = """
        {
          "content": [{
            "no": "1",
            "datetime": "2026-03-09T10:00:00",
            "noticeTypeName": "수시공시",
            "title": "공지 기사",
            "contents": "공지 내용",
            "endUrl": "https://stock.naver.com/domestic/stock/005930/price"
          }],
          "totalPages": 1,
          "totalElements": 1,
          "last": true,
          "number": 0,
          "size": 10
        }
    """.trimIndent()

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
            industryResearchUrl = "$baseUrl/research/industry-research",
            discussionRankingUrl = "$baseUrl/community/discussion/rankings",
            researchRankingUrl = "$baseUrl/research/ranking",
            newsListUrl = "$baseUrl/news/list",
            newsFocusUrl = "$baseUrl/news/focus",
            worldNewsUrl = "$baseUrl/foreign/news/worldNews",
            moneyStoryUrl = "$baseUrl/content/moneyStory",
            domesticDetailNewsUrl = "$baseUrl/domestic/detail/news",
            domesticStockDetailUrl = "$baseUrl/domestic/detail",
            foreignStockNewsUrl = "$baseUrl/foreign/worldStock/list",
            foreignStockOverviewUrl = "$baseUrl/securityService/stock",
            foreignStockBasicUrl = "$baseUrl/securityService/stock",
            noticeListUrl = "$baseUrl/news/noticeList",
            newsSearchUrl = "$baseUrl/news/search"
        )
    }
}
