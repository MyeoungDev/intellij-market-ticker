package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.fixtures.naver.NaverThemeFixtures as Fixtures
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

class NaverClientThemeSummaryTest {
    @Test
    fun `production requests use only general host and eight second timeout`() {
        val transport = RecordingClient()
        val client = NaverClient(client = transport)
        assertThatThrownBy { client.fetchThemeRankingsPage("a+/=% ") }.isInstanceOf(IOException::class.java)
        assertThatThrownBy { client.fetchThemeMembers("a/b ?") }.isInstanceOf(IOException::class.java)
        assertThat(transport.requests.map { it.uri().host }).containsOnly("stock.naver.com")
        assertThat(transport.requests.map { it.uri().scheme }).containsOnly("https")
        assertThat(transport.requests.map { it.timeout().get() }).containsOnly(Duration.ofSeconds(8))
        assertThat(transport.requests.first().uri().rawQuery)
            .isEqualTo("sortType=changeRate&size=100&excludeCodes=25&period=daily&cursor=a%2B%2F%3D%25+")
        assertThat(transport.requests.last().uri().rawPath)
            .isEqualTo("/api/domestic/market/theme/a%2Fb%20%3F/stocklist")
        assertThat(transport.requests.last().uri().rawQuery)
            .isEqualTo("marketType=ALL&orderType=quantTop&startIdx=0&pageSize=20")
    }

    @Test
    fun `timeout overrides cannot increase eight second bound`() {
        val transport = RecordingClient()
        val client = NaverClient(client = transport)
        listOf(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(9)).forEach { timeout ->
            assertThatThrownBy { client.fetchThemeRankingsPage(timeout = timeout) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun `chain default deadline prevents any page after thirty seconds`() {
        val transport = RecordingClient()
        var reads = 0
        val provider = NaverThemeSummaryProvider(NaverClient(client = transport),
            nanoTime = { if (reads++ == 0) 0L else Duration.ofSeconds(30).toNanos() })
        assertThatThrownBy { provider.getDailySummary() }.hasMessageContaining("deadline")
        assertThat(transport.requests).isEmpty()
    }

    @Test
    fun `already interrupted caller performs no network request`() {
        val transport = RecordingClient()
        Thread.currentThread().interrupt()
        try {
            assertThatThrownBy { NaverClient(client = transport).fetchThemeMembers("566") }
                .isInstanceOf(InterruptedException::class.java)
            assertThat(transport.requests).isEmpty()
        } finally {
            Thread.interrupted()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "{}", "not-json", "[null]", "[{\"itemcode\":{}}]",
        "[{\"itemcode\":false,\"itemname\":true}]"])
    fun `member transport rejects malformed schema`(body: String) {
        withServer { server, client ->
            server.stubFor(get(urlPathEqualTo(MEMBER_PATH)).willReturn(okJson(body)))
            assertThatThrownBy { client.fetchThemeMembers("566") }.isInstanceOf(Exception::class.java)
        }
    }

    @Test
    fun `member failure remains explicit and empty member success remains valid`() {
        withServer { server, client ->
            server.stubFor(get(urlPathEqualTo(MEMBER_PATH)).willReturn(aResponse().withStatus(500)))
            assertThatThrownBy { client.fetchThemeMembers("566") }.hasMessageContaining("500")
            server.stubFor(get(urlPathEqualTo(MEMBER_PATH)).willReturn(okJson("[]")))
            assertThat(client.fetchThemeMembers("566")).isEmpty()
        }
    }

    @Test
    fun `oversized pages fail instead of silently truncating`() {
        withServer { server, client ->
            server.stubFor(get(urlPathEqualTo(RANK_PATH)).willReturn(okJson(
                Fixtures.page((1..101).map { Fixtures.item("$it", "1") }))))
            assertThatThrownBy { client.fetchThemeRankingsPage() }.hasMessageContaining("Invalid")
            server.stubFor(get(urlPathEqualTo(MEMBER_PATH)).willReturn(okJson(
                "[" + (1..21).joinToString(",") { "{}" } + "]")))
            assertThatThrownBy { client.fetchThemeMembers("566") }.hasMessageContaining("Invalid")
        }
    }

    private fun withServer(test: (WireMockServer, NaverClient) -> Unit) {
        val server = WireMockServer(wireMockConfig().dynamicPort())
        server.start()
        try {
            test(server, NaverClient(
                themeRankingsUrl = "${server.baseUrl()}$RANK_PATH",
                themeMembersUrl = "${server.baseUrl()}/api/domestic/market/theme",
                client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()))
        } finally {
            server.stop()
        }
    }

    private class RecordingClient : HttpClient() {
        val requests = mutableListOf<HttpRequest>()
        override fun <T> send(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
            requests.add(request)
            throw IOException("Recorded without networking")
        }
        override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
        override fun connectTimeout(): Optional<Duration> = Optional.empty()
        override fun followRedirects() = Redirect.NEVER
        override fun proxy(): Optional<ProxySelector> = Optional.empty()
        override fun sslContext(): SSLContext = SSLContext.getDefault()
        override fun sslParameters() = SSLParameters()
        override fun authenticator(): Optional<Authenticator> = Optional.empty()
        override fun version() = Version.HTTP_2
        override fun executor(): Optional<Executor> = Optional.empty()
        override fun <T> sendAsync(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>): CompletableFuture<HttpResponse<T>> =
            throw UnsupportedOperationException()
        override fun <T> sendAsync(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>,
            pushPromiseHandler: HttpResponse.PushPromiseHandler<T>): CompletableFuture<HttpResponse<T>> =
            throw UnsupportedOperationException()
    }

    companion object {
        private const val RANK_PATH = "/api/stockSecurity/rankings/v2/domestic/themes"
        private const val MEMBER_PATH = "/api/domestic/market/theme/566/stocklist"
    }
}
