package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.MarketType
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
            worldPriceUrl = "$baseUrl/worldstock/stock"
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
