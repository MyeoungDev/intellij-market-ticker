package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.application.service.PriceHistoryService
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.fixtures.domain.TickerFixtures
import com.github.myeoungdev.marketticker.fixtures.naver.NaverFixtures
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverSearchResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Fault
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.http.HttpClient
import java.time.LocalDateTime

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2026-01-23
 */
@DisplayName("Naver API 및 Client 통합 테스트")
class NaverClientTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var naverClient: NaverClient

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMockServer.start()

        val baseUrl = wireMockServer.baseUrl()

        naverClient = NaverClient(
            client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(500))
                .build(),
            searchBaseUrl = "$baseUrl/search",
            domesticPriceUrl = "$baseUrl/domestic/stock",
            worldPriceUrl = "$baseUrl/worldstock/stock",
            coinPriceUrl = "$baseUrl/coin/price",
            cryptoChartUrl = "$baseUrl/chart/cryptoChartData",
            domesticIndexUrl = "$baseUrl/domestic/index",
            worldIndexUrl = "$baseUrl/worldstock/index",
            marketMetalUrl = "$baseUrl/marketindex/metals",
            marketEnergyUrl = "$baseUrl/marketindex/energy",
            domesticChartUrl = "$baseUrl/chart/domestic/item",
            foreignChartUrl = "$baseUrl/chart/foreign/item",
            newsListUrl = "$baseUrl/news/list",
            newsAggregateUrl = "$baseUrl/news/aggregate/home"
        )
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Nested
    @DisplayName("1. DTO 매핑 및 JSON 파싱 검증")
    inner class DtoMappingTest {

        @Test
        fun `NaverSearchItem 결과에서 Ticker 도메인 변환 검증`() {
            // Given
            val item = NaverFixtures.createSearchItem(
                code = "005930",
                name = "삼성전자",
                typeCode = "KOSPI",
                nationCode = "KOR"
            )

            // When
            val ticker = item.toTicker()

            // Then
            assertThat(ticker.symbol).isEqualTo("005930")
            assertThat(ticker.marketType).isEqualTo(MarketType.KOSPI)
            assertThat(ticker.nationCode).isEqualTo("KOR")
        }

        @Test
        fun `코인 검색 결과는 거래소 타입을 marketType으로 변환한다`() {
            val item = NaverFixtures.createSearchItem(
                code = "BTC",
                name = "비트코인",
                typeCode = "UPBIT",
                reutersCode = "BTC_KRW_UPBIT",
                nationCode = null,
                nationName = null,
                category = "coin"
            )

            val ticker = item.toTicker()

            assertThat(ticker.symbol).isEqualTo("BTC")
            assertThat(ticker.tradingSymbol).isEqualTo("BTC_KRW_UPBIT")
            assertThat(ticker.marketType).isEqualTo(MarketType.UPBIT)
        }

        @Test
        fun `NaverStockPrice 결과에서 TickerPrice 도메인 변환 검증 (나스닥 케이스)`() {
            // Given
            val naverStock = NaverFixtures.PRICE_TESLA_FALLING

            // When
            val tickerPrice = naverStock.toTickerPrice()

            // Then
            assertThat(tickerPrice.symbol).isEqualTo("TSLA")
            assertThat(tickerPrice.marketType).isEqualTo(MarketType.NASDAQ)
            assertThat(tickerPrice.currency.code).isEqualTo("USD")
            assertThat(tickerPrice.currentPrice).isEqualTo(180.50)
        }

        @Test
        fun `JSON 문자열이 DTO로 정상적으로 역직렬화 된다`() {
            // Given
            val json = NaverFixtures.JSON_SEARCH_SUCCESS_SAMSUNG

            // When
            val response: NaverSearchResponse = objectMapper.readValue(json)

            // Then
            assertThat(response.isSuccess).isTrue()
            assertThat(response.result?.items).hasSize(1)
            assertThat(response.result?.items?.get(0)?.name).isEqualTo("삼성전자")
        }

        @Test
        fun `지표 등락률이 없으면 등락폭으로 퍼센트를 백업 계산한다`() {
            val item = com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverMarketIndicatorItem(
                itemCode = "TEST",
                stockName = "테스트 지표",
                closePrice = "90.00",
                fluctuationsRatio = "",
                compareToPreviousClosePrice = null,
                fluctuations = "-10.00",
                marketStatus = "OPEN"
            )

            val indicator = item.toMarketIndicator(IndicatorCategory.ENERGY)
            assertThat(indicator.changeRate).isEqualTo(-10.0)
        }
    }

    @Nested
    @DisplayName("2. Client 동작 및 네트워크 시나리오")
    inner class ClientScenarioTest {

        @Test
        fun `검색 API 정상 응답 시 NaverSearchItem 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(urlPathMatching("/search.*"))
                    .withQueryParam("query", containing("삼성"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_SEARCH_SUCCESS_SAMSUNG)
                    )
            )

            // When
            val result = naverClient.searchStocks("삼성")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("삼성전자")
        }

        @Test
        fun `검색 API 결과가 없으면 빈 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(urlPathMatching("/search.*"))
                    .withQueryParam("query", containing("삼성"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_SEARCH_EMPTY)
                    )
            )

            // When
            val result = naverClient.searchStocks("존재하지않는종목")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `시세 조회 API 국내 시세 요청 성공 시 데이터를 파싱하여 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(urlPathMatching("/domestic/stock.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_PRICE_DOMESTIC_SUCCESS)
                    )
            )

            wireMockServer.stubFor(
                get(urlPathMatching("/worldstock/stock.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_PRICE_EMPTY_SUCCESS)
                    )
            )

            // When
            val result = naverClient.fetchStockPrice(listOf(TickerFixtures.SAMSUNG_ELECTRONICS))

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].itemCode).isEqualTo("005930")
            assertThat(result[0].closePrice).isEqualTo("103,400")
        }

        @Test
        fun `코인 시세 조회 API 성공 시 코인 데이터를 파싱한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/coin/price.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_PRICE_COIN_SUCCESS)
                    )
            )

            val result = naverClient.fetchCoinPrice(
                listOf(
                    Ticker(
                        symbol = "BTC",
                        tradingSymbol = "BTC_KRW_UPBIT",
                        name = "비트코인",
                        marketType = MarketType.UPBIT,
                        nationCode = null,
                        nationName = null
                    )
                )
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].fqnfTicker).isEqualTo("BTC_KRW_UPBIT")
            assertThat(result[0].tradePrice).isGreaterThan(0.0)
        }

        @Test
        fun `코인 차트 API 성공 시 캔들 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/chart/cryptoChartData.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_CHART_COIN_SUCCESS)
                    )
            )

            val result = naverClient.fetchCryptoChartCandles(
                exchangeType = "UPBIT",
                nfTicker = "BTC",
                marketType = "KRW",
                from = LocalDateTime.parse("2026-03-03T00:00:00")
            )

            assertThat(result).isNotEmpty
            assertThat(result.first().candleId).contains("BTC_KRW_UPBIT")
        }

        @Test
        fun `국내 지수 API 성공 시 지수 데이터를 파싱한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/domestic/index.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_DOMESTIC_INDEX_SUCCESS)
                    )
            )

            val result = naverClient.fetchDomesticIndices(listOf("KOSPI", "KOSDAQ"))

            assertThat(result.datas).hasSize(1)
            assertThat(result.datas.first().itemCode).isEqualTo("KOSPI")
            assertThat(result.datas.first().closePrice).isEqualTo("5,791.91")
        }

        @Test
        fun `원자재 API 성공 시 금속 지표를 파싱한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/marketindex/metals/GCcv1.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_MARKET_METAL_SUCCESS)
                    )
            )

            val result = naverClient.fetchMarketCommodity("metals", "GCcv1")

            assertThat(result.datas).hasSize(1)
            assertThat(result.datas.first().reutersCode).isEqualTo("GCcv1")
            assertThat(result.datas.first().name).isEqualTo("국제 금")
        }

        @Test
        fun `국내 주식 차트 API 성공 시 day 캔들 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/chart/domestic/item/005930/day.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_CHART_DOMESTIC_DAY_SUCCESS)
                    )
            )

            val result = naverClient.fetchStockChartCandles(
                ticker = TickerFixtures.SAMSUNG_ELECTRONICS,
                period = PriceHistoryService.Period.DAY
            )

            assertThat(result).hasSize(1)
            assertThat(result.first().localDate).isEqualTo("20260305")
            assertThat(result.first().closePrice).isEqualTo(9230.0)
        }

        @Test
        fun `해외 주식 차트 API 성공 시 year 캔들 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/chart/foreign/item/AAPL.O/year.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_CHART_FOREIGN_YEAR_SUCCESS)
                    )
            )

            val result = naverClient.fetchStockChartCandles(
                ticker = TickerFixtures.APPLE,
                period = PriceHistoryService.Period.YEAR
            )

            assertThat(result).hasSize(1)
            assertThat(result.first().localDate).isEqualTo("20100101")
            assertThat(result.first().closePrice).isEqualTo(0.385)
        }

        @Test
        fun `뉴스 리스트 API 성공 시 기사 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/news/list.*"))
                    .withQueryParam("category", equalTo("FLASHNEWS"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_NEWS_LIST_FLASH_SUCCESS)
                    )
            )

            val result = naverClient.fetchNewsList(category = "FLASHNEWS")

            assertThat(result).hasSize(1)
            assertThat(result.first().title).isEqualTo("테스트 뉴스 제목")
            assertThat(result.first().articleUrl()).isEqualTo("https://n.news.naver.com/article/003/0013805290")
        }

        @Test
        fun `랭킹 뉴스 API 성공 시 랭킹 기사 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/news/aggregate/home.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_NEWS_AGGREGATE_RANKING_SUCCESS)
                    )
            )

            val result = naverClient.fetchRankingNews(limit = 5)

            assertThat(result).hasSize(1)
            assertThat(result.first().title).isEqualTo("랭킹 뉴스 테스트")
            assertThat(result.first().articleUrl()).isEqualTo("https://n.news.naver.com/article/015/0005258767")
        }
    }

    @Test
    fun `시세 조회 API 국내, 해외 시세 요청 성공 시 결과를 병합해서 반환한다`() {
        // Given
        wireMockServer.stubFor(
            get(urlPathMatching("/domestic/stock.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NaverFixtures.JSON_PRICE_DOMESTIC_SUCCESS)
                )
        )

        wireMockServer.stubFor(
            get(urlPathMatching("/worldstock/stock.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NaverFixtures.JSON_PRICE_WORLD_SUCCESS)
                )
        )

        // When
        val result = naverClient.fetchStockPrice(listOf(TickerFixtures.SAMSUNG_ELECTRONICS, TickerFixtures.APPLE))

        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].itemCode).isEqualTo("005930")
        assertThat(result[0].stockName).isEqualTo("삼성전자")
        assertThat(result[1].itemCode).isEqualTo("AAPL")
        assertThat(result[1].stockName).isEqualTo("애플")

    }

    @Nested
    @DisplayName("3. 네트워크 장애 및 예외 처리")
    inner class ErrorHandlingTest {

        @Test
        fun `검색 API HTTP 500 서버 오류 발생 시 빈 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withStatus(500))
            )

            // When
            val result = naverClient.searchStocks("삼성")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `시세 조회 API HTTP 404 잘못된 URL 요청 시 빈 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withStatus(404))
            )

            // When
            val result = naverClient.fetchStockPrice(listOf(TickerFixtures.SAMSUNG_ELECTRONICS))

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `검색 API 네트워크 오류(Connection Reset) 발생 시 예외를 잡고 빈 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(anyUrl())
                    .willReturn(
                        aResponse()
                            .withFault(Fault.CONNECTION_RESET_BY_PEER)
                    )
            )

            // When
            val result = naverClient.searchStocks("지연")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `API 응답은 200이지만 isSuccess=false인 경우 빈 리스트 반환`() {
            // Given
            val errorResponse = NaverFixtures.createSearchResponse(
                isSuccess = false,
                message = "Invalid Query",
                result = null
            )

            wireMockServer.stubFor(
                get(urlPathMatching("/domestic/stock.*"))
                    .withQueryParam("query", containing("삼성전자"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(objectMapper.writeValueAsString(errorResponse))
                    )
            )

            // When
            val result = naverClient.searchStocks("오류")

            // Then
            assertThat(result).isEmpty()
        }
    }
}
