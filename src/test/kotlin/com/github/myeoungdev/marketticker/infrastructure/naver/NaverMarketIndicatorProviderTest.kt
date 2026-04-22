package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.fixtures.naver.NaverFixtures
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.http.HttpClient

@DisplayName("Naver 시장 지표 Provider 테스트")
class NaverMarketIndicatorProviderTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var provider: NaverMarketIndicatorProvider

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMockServer.start()

        val baseUrl = wireMockServer.baseUrl()
        val client = NaverClient(
            client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(500))
                .build(),
            domesticIndexUrl = "$baseUrl/domestic/index",
            worldIndexUrl = "$baseUrl/worldstock/index",
            marketMetalUrl = "$baseUrl/marketindex/metals",
            marketEnergyUrl = "$baseUrl/marketindex/energy",
            exchangeRateUrl = "$baseUrl/domestic/exchange/List",
        )
        provider = NaverMarketIndicatorProvider(client)
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `환율을 포함해 Naver 시장 지표를 반환한다`() {
        stubBaseIndicators()
        stubExchangeRates(NaverFixtures.JSON_EXCHANGE_RATE_SUCCESS, status = 200)

        val result = provider.getIndicators()

        val exchangeRates = result.filter { it.category == IndicatorCategory.EXCHANGE_RATE }
        assertThat(exchangeRates.map { it.code })
            .containsExactly("FX_USDKRW", "FX_JPYKRW", "FX_EURKRW", "FX_CNYKRW", "FX_HKDKRW")
        assertThat(exchangeRates.first().name).isEqualTo("USD")
        assertThat(exchangeRates.first().currentPrice).isEqualTo(1477.60)
        assertThat(exchangeRates.first().changeRate).isEqualTo(-0.30)
    }

    @Test
    fun `환율 API 실패 시 다른 지표만 반환한다`() {
        stubBaseIndicators()
        stubExchangeRates("", status = 503)

        val result = provider.getIndicators()

        assertThat(result).isNotEmpty
        assertThat(result.none { it.category == IndicatorCategory.EXCHANGE_RATE }).isTrue()
    }

    private fun stubBaseIndicators() {
        stubIndicator("/domestic/index.*", NaverFixtures.JSON_DOMESTIC_INDEX_SUCCESS)
        stubIndicator("/worldstock/index.*", NaverFixtures.JSON_DOMESTIC_INDEX_SUCCESS)
        stubIndicator("/marketindex/metals/GCcv1.*", NaverFixtures.JSON_MARKET_METAL_SUCCESS)
        stubIndicator("/marketindex/energy/CLcv1.*", NaverFixtures.JSON_MARKET_METAL_SUCCESS)
    }

    private fun stubIndicator(path: String, body: String) {
        wireMockServer.stubFor(
            get(urlPathMatching(path))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )
    }

    private fun stubExchangeRates(body: String, status: Int) {
        wireMockServer.stubFor(
            get(urlEqualTo("/domestic/exchange/List"))
                .willReturn(
                    aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )
    }
}
