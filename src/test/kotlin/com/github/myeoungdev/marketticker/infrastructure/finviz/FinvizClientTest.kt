package com.github.myeoungdev.marketticker.infrastructure.finviz

import com.github.myeoungdev.marketticker.domain.model.calendar.CalendarType
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

@DisplayName("FinvizClient 파싱 및 네트워크 테스트")
class FinvizClientTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var finvizClient: FinvizClient

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMockServer.start()

        val baseUrl = wireMockServer.baseUrl()
        finvizClient = FinvizClient(
            client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build(),
            calendarBaseUrl = "$baseUrl/calendar"
        )
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `실적 캘린더 route init 데이터를 파싱한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/calendar/earnings"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(
                            """
                            <html><body>
                            <script id="route-init-data" type="application/json">
                            {
                              "data":{
                                "entries":{
                                  "items":[
                                    {
                                      "earningsDate":"2026-04-03T08:30:00",
                                      "ticker":"RCMT",
                                      "company":"RCM Technologies, Inc",
                                      "epsEstimate":0.62,
                                      "epsActual":0.77,
                                      "salesEstimate":84.461,
                                      "salesActual":86.476,
                                      "boxoverData":{"industry":"Conglomerates","country":"USA"}
                                    }
                                  ]
                                }
                              }
                            }
                            </script>
                            </body></html>
                            """.trimIndent()
                        )
                )
        )

        val result = finvizClient.fetchCalendar(CalendarType.EARNINGS, limit = 10)

        assertThat(result).hasSize(1)
        assertThat(result.first().ticker).isEqualTo("RCMT")
        assertThat(result.first().title).isEqualTo("RCM Technologies, Inc")
        assertThat(result.first().actual).contains("EPS 0.77")
        assertThat(result.first().forecast).contains("Sales 84.461")
    }

    @Test
    fun `경제 캘린더 route init 데이터를 파싱한다`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/calendar/economic"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(
                            """
                            <html><body>
                            <script id="route-init-data" type="application/json">
                            {
                              "data":{
                                "entries":[
                                  {
                                    "event":"Non Farm Payrolls",
                                    "category":"Non Farm Payrolls",
                                    "date":"2026-04-03T08:30:00",
                                    "actual":"178K",
                                    "previous":"-133K",
                                    "forecast":"60K",
                                    "importance":3
                                  }
                                ]
                              }
                            }
                            </script>
                            </body></html>
                            """.trimIndent()
                        )
                )
        )

        val result = finvizClient.fetchCalendar(CalendarType.ECONOMIC, limit = 10)

        assertThat(result).hasSize(1)
        assertThat(result.first().title).isEqualTo("Non Farm Payrolls")
        assertThat(result.first().subtitle).isEqualTo("Non Farm Payrolls")
        assertThat(result.first().actual).isEqualTo("178K")
        assertThat(result.first().impact).isEqualTo(3)
    }
}
