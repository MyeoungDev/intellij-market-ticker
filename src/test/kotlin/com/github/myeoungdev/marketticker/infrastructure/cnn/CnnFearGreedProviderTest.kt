package com.github.myeoungdev.marketticker.infrastructure.cnn

import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpClient.Version
import java.net.http.HttpClient
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

class CnnFearGreedProviderTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var provider: CnnFearGreedProvider

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMockServer.start()

        provider = CnnFearGreedProvider(
            client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(500))
                .build(),
            endpoint = "${wireMockServer.baseUrl()}/index/fearandgreed/graphdata"
        )
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `CNN 공포 탐욕 지수를 반환한다`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/index/fearandgreed/graphdata"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                                {
                                  "fear_and_greed": {
                                    "score": 25.4571428571429,
                                    "rating": "fear",
                                    "timestamp": "2026-06-25T23:59:59+00:00",
                                    "previous_close": 25.9142857142857,
                                    "previous_1_week": 37.5714285714286,
                                    "previous_1_month": 58.9714285714286,
                                    "previous_1_year": 59.25714285714286
                                  }
                                }
                            """.trimIndent()
                        )
                )
        )

        val result = provider.getIndicators()

        assertThat(result).hasSize(1)
        assertThat(result.single().code).isEqualTo("FNG")
        assertThat(result.single().category).isEqualTo(IndicatorCategory.SENTIMENT)
        assertThat(result.single().name).isEqualTo("Fear & Greed")
        assertThat(result.single().currentPrice).isEqualTo(25.4571428571429)
        assertThat(result.single().changeRate).isEqualTo(0.0)
        assertThat(result.single().sentimentScore).isEqualTo(25.4571428571429)
        assertThat(result.single().sentimentLabel).isEqualTo("공포")
        assertThat(result.single().marketStatus).isEqualTo(MarketStatus.CLOSED)
    }

    @Test
    fun `응답 코드가 200이 아니면 빈 목록을 반환한다`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/index/fearandgreed/graphdata"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                )
        )

        assertThat(provider.getIndicators()).isEmpty()
    }

    @Test
    fun `JSON 이 깨져 있으면 빈 목록을 반환한다`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/index/fearandgreed/graphdata"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not-json")
                )
        )

        assertThat(provider.getIndicators()).isEmpty()
    }

    @Test
    fun `fear and greed payload 가 없으면 빈 목록을 반환한다`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/index/fearandgreed/graphdata"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"foo":"bar"}""")
                )
        )

        assertThat(provider.getIndicators()).isEmpty()
    }

    @Test
    fun `인터럽트면 플래그를 복구하고 빈 목록을 반환한다`() {
        val interruptedProvider = CnnFearGreedProvider(client = interruptedHttpClient())

        try {
            assertThat(interruptedProvider.getIndicators()).isEmpty()
            assertThat(Thread.currentThread().isInterrupted()).isTrue()
        } finally {
            Thread.interrupted()
        }
    }

    private fun interruptedHttpClient(): HttpClient {
        return object : HttpClient() {
            override fun <T : Any?> send(
                request: HttpRequest,
                responseBodyHandler: HttpResponse.BodyHandler<T>
            ): HttpResponse<T> {
                throw InterruptedException("interrupted")
            }

            override fun <T : Any?> sendAsync(
                request: HttpRequest,
                responseBodyHandler: HttpResponse.BodyHandler<T>
            ): CompletableFuture<HttpResponse<T>> {
                throw UnsupportedOperationException()
            }

            override fun <T : Any?> sendAsync(
                request: HttpRequest,
                responseBodyHandler: HttpResponse.BodyHandler<T>,
                pushPromiseHandler: HttpResponse.PushPromiseHandler<T>?
            ): CompletableFuture<HttpResponse<T>> {
                throw UnsupportedOperationException()
            }

            override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
            override fun connectTimeout(): Optional<Duration> = Optional.empty()
            override fun followRedirects(): HttpClient.Redirect = HttpClient.Redirect.NEVER
            override fun proxy(): Optional<ProxySelector> = Optional.empty()
            override fun authenticator(): Optional<Authenticator> = Optional.empty()
            override fun version(): Version = Version.HTTP_1_1
            override fun sslContext(): SSLContext = SSLContext.getDefault()
            override fun sslParameters(): SSLParameters = SSLParameters()
            override fun executor(): Optional<Executor> = Optional.empty()
        }
    }
}
