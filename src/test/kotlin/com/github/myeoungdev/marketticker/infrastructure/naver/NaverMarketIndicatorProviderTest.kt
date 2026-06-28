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

        val metals = result.filter { it.category == IndicatorCategory.METAL }
        assertThat(metals.map { it.code }).containsExactly("GC", "SI", "HG", "PL", "PA")

        val energy = result.filter { it.category == IndicatorCategory.ENERGY }
        assertThat(energy.map { it.code }).containsExactly("CL", "NG", "HO", "RB")

        val worldIndices = result.filter { it.category == IndicatorCategory.WORLD_INDEX }
        assertThat(worldIndices.map { it.code }).containsExactly("DJI", "INX", "IXIC", "SOX", "VIX")
        assertThat(worldIndices.first().name).isEqualTo("DOW")
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
        stubIndicator("/worldstock/index.*", worldIndicatorResponse())
        stubIndicator("/marketindex/metals/GCcv1.*", commodityResponse("GCcv1", "GC", "국제 금", "5,081.20", "-230.40", "-4.34", "USD/OZS"))
        stubIndicator("/marketindex/metals/SIcv1.*", commodityResponse("SIcv1", "SI", "은", "76.41", "0.91", "1.21", "USD/OZS"))
        stubIndicator("/marketindex/metals/HGcv1.*", commodityResponse("HGcv1", "HG", "구리(선물)", "6.0270", "-0.0545", "-0.90", "USD/LBS"))
        stubIndicator("/marketindex/metals/PLcv1.*", commodityResponse("PLcv1", "PL", "백금", "2,030.40", "-8.00", "-0.39", "USD/OZS"))
        stubIndicator("/marketindex/metals/PAcv1.*", commodityResponse("PAcv1", "PA", "팔라듐", "1,509.90", "16.30", "1.09", "USD/OZS"))
        stubIndicator("/marketindex/energy/CLcv1.*", commodityResponse("CLcv1", "CL", "WTI", "94.40", "-1.45", "-1.51", "USD/BBL"))
        stubIndicator("/marketindex/energy/NGcv1.*", commodityResponse("NGcv1", "NG", "천연가스", "2.68", "-0.08", "-2.79", "USD/MMBTU"))
        stubIndicator("/marketindex/energy/HOcv1.*", commodityResponse("HOcv1", "HO", "난방유", "3.7943", "-0.0734", "-1.90", "USD/U GAL"))
        stubIndicator("/marketindex/energy/RBcv1.*", commodityResponse("RBcv1", "RB", "RBOB 가솔린", "3.3277", "-0.0065", "-0.19", "USD/U GAL"))
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

    private fun worldIndicatorResponse(): String {
        return """
            {
              "pollingInterval": 7000,
              "datas": [
                {
                  "reutersCode": ".DJI",
                  "symbolCode": "DJI",
                  "indexName": "DOW",
                  "closePrice": "43,821.39",
                  "fluctuations": "120.10",
                  "fluctuationsRatio": "0.27",
                  "marketStatus": "OPEN"
                },
                {
                  "reutersCode": ".INX",
                  "symbolCode": "INX",
                  "indexName": "S&P500",
                  "closePrice": "6,102.12",
                  "fluctuations": "-18.10",
                  "fluctuationsRatio": "-0.30",
                  "marketStatus": "OPEN"
                },
                {
                  "reutersCode": ".IXIC",
                  "symbolCode": "IXIC",
                  "indexName": "NASDAQ",
                  "closePrice": "20,045.33",
                  "fluctuations": "98.44",
                  "fluctuationsRatio": "0.49",
                  "marketStatus": "OPEN"
                },
                {
                  "reutersCode": ".SOX",
                  "symbolCode": "SOX",
                  "indexName": "필라델피아 반도체",
                  "closePrice": "5,012.88",
                  "fluctuations": "77.11",
                  "fluctuationsRatio": "1.56",
                  "marketStatus": "OPEN"
                },
                {
                  "reutersCode": ".VIX",
                  "symbolCode": "VIX",
                  "indexName": "VIX",
                  "closePrice": "14.11",
                  "fluctuations": "-0.18",
                  "fluctuationsRatio": "-1.26",
                  "marketStatus": "OPEN"
                }
              ],
              "time": "20260425185432"
            }
        """.trimIndent()
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

    private fun commodityResponse(
        reutersCode: String,
        symbolCode: String,
        name: String,
        closePrice: String,
        fluctuations: String,
        fluctuationsRatio: String,
        unit: String
    ): String {
        return """
            {
              "pollingInterval": 7000,
              "datas": [
                {
                  "reutersCode": "$reutersCode",
                  "symbolCode": "$symbolCode",
                  "name": "$name",
                  "closePrice": "$closePrice",
                  "fluctuations": "$fluctuations",
                  "fluctuationsRatio": "$fluctuationsRatio",
                  "openPrice": "$closePrice",
                  "highPrice": "$closePrice",
                  "lowPrice": "$closePrice",
                  "accumulatedTradingVolume": "1",
                  "marketStatus": "OPEN",
                  "unit": "$unit"
                }
              ],
              "time": "20260425185432"
            }
        """.trimIndent()
    }
}
