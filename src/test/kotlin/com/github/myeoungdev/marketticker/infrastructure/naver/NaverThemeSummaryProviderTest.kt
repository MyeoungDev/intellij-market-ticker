package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.fixtures.naver.NaverThemeFixtures as Fixtures
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.http.HttpTimeoutException
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class NaverThemeSummaryProviderTest {
    private lateinit var server: WireMockServer
    private lateinit var client: NaverClient
    private val fetchedAt = Instant.parse("2026-10-06T07:30:00Z")
    private val clock = Clock.fixed(fetchedAt, ZoneOffset.UTC)
    private val path = "/api/stockSecurity/rankings/v2/domestic/themes"

    @BeforeEach
    fun setUp() {
        server = WireMockServer(wireMockConfig().dynamicPort())
        server.start()
        client = NaverClient(
            themeRankingsUrl = "${server.baseUrl()}/api/stockSecurity/rankings/v2/domestic/themes",
            themeMembersUrl = "${server.baseUrl()}/api/domestic/market/theme",
            client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build())
    }

    @AfterEach
    fun tearDown() = server.stop()

    @Test
    fun `complete sequential traversal ranks exact bottom three and preserves opaque cursors`() {
        val cursor = "a+/= ?&%"
        stub(Fixtures.page(listOf(Fixtures.item("a", "8"), Fixtures.item("b", "8"),
            Fixtures.item("c", "6"), Fixtures.item("d", "1"), Fixtures.item("zero", "0"),
            Fixtures.item("x", "-1")), true, cursor))
        stub(Fixtures.page(listOf(Fixtures.item("y", "-5")), true, "next"), cursor)
        stub(Fixtures.page(listOf(Fixtures.item("z", "-9"), Fixtures.item("w", "-8"))), "next")

        val summary = provider().getDailySummary()

        assertThat(summary.gainers.map { it.code }).containsExactly("a", "b", "c")
        assertThat(summary.losers.map { it.code }).containsExactly("z", "w", "y")
        assertThat(summary.fetchedAt).isEqualTo(fetchedAt)
        assertThat(summary.gainers.first().updatedAt).isEqualTo(Instant.parse("2026-10-06T07:00:00Z"))
        assertThat(summary.gainers.first().risingCount).isEqualTo(3)
        assertThat(summary.gainers.first().fallingCount).isEqualTo(2)
        assertThat(summary.gainers.first().unchangedCount).isEqualTo(1)
        val requests = server.allServeEvents.sortedBy { it.request.loggedDate }.map { it.request }
        assertThat(requests).hasSize(3)
        assertThat(requests.map { if (it.queryParameter("cursor").isPresent) it.queryParameter("cursor").firstValue() else null })
            .containsExactly(null, cursor, "next")
        requests.forEach {
            assertThat(it.queryParams.keys).containsExactlyInAnyOrderElementsOf(
                listOf("sortType", "size", "excludeCodes", "period") +
                    if (it.queryParameter("cursor").isPresent) listOf("cursor") else emptyList())
            assertThat(it.queryParameter("sortType").firstValue()).isEqualTo("changeRate")
            assertThat(it.queryParameter("size").firstValue()).isEqualTo("100")
            assertThat(it.queryParameter("excludeCodes").firstValue()).isEqualTo("25")
            assertThat(it.queryParameter("period").firstValue()).isEqualTo("daily")
        }
    }

    @Test
    fun `newest duplicate wins without changing original tie order`() {
        stub(Fixtures.page(listOf(Fixtures.item("a", "8"), Fixtures.item("b", "5")), true, "next"))
        stub(Fixtures.page(listOf(Fixtures.item("a", "5", "2026-10-06T16:01:00+09:00"),
            Fixtures.item("b", "99", "2026-10-06T15:00:00+09:00"))), "next")
        val summary = provider().getDailySummary()
        assertThat(summary.gainers.map { it.code }).containsExactly("a", "b")
        assertThat(summary.gainers.map { it.changeRate }).containsExactly(5.0, 5.0)
        assertThat(summary.losers).isEmpty()
    }

    @Test
    fun `optional leaders are skipped without losing theme and capped at three`() {
        val item = Fixtures.item("a", "1").dropLast(1) + """,
            "topByChangeRate":[null,{}, {"code":"","name":"Blank","value":"1"},
            {"code":"bad","name":"Bad","value":"NaN"},
            {"code":"one","name":"One","value":"2.5"},
            {"code":"two","name":"Two","value":"1"},
            {"code":"three","name":"Three","value":"0"},
            {"code":"four","name":"Four","value":"-1"}]}"""
        stub(Fixtures.page(listOf(item)))
        val theme = provider().getDailySummary().gainers.single()
        assertThat(theme.leaders.map { it.ticker.symbol }).containsExactly("one", "two", "three")
        assertThat(theme.leaders.first().ticker.name).isEqualTo("One")
        assertThat(theme.leaders.first().changeRate).isEqualTo(2.5)
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", "null", "[]", "not-json", "{\"hasNext\":false}",
        "{\"hasNext\":\"false\",\"items\":[]}", "{\"hasNext\":false,\"items\":null}"])
    fun `malformed pages fail explicitly`(body: String) {
        stub(body)
        assertThatThrownBy { provider().getDailySummary() }.isInstanceOf(Exception::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["code", "name", "changeRate", "risingCount", "fallingCount", "unchangedCount", "updatedAt"])
    fun `missing required item field fails including a duplicate`(field: String) {
        val item = com.github.myeoungdev.marketticker.common.config.objectMapper.readTree(Fixtures.item("a", "1"))
        (item as com.fasterxml.jackson.databind.node.ObjectNode).remove(field)
        stub(Fixtures.page(listOf(Fixtures.item("a", "2"), item.toString())))
        assertThatThrownBy { provider().getDailySummary() }.isInstanceOf(Exception::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["NaN", "Infinity", "-Infinity", "oops", ""])
    fun `invalid returns fail`(rate: String) {
        stub(Fixtures.page(listOf(Fixtures.item("a", rate))))
        assertThatThrownBy { provider().getDailySummary() }.isInstanceOf(IllegalStateException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["-1", "1.5", "NaN", "2147483648", ""])
    fun `invalid counts fail`(count: String) {
        stub(Fixtures.page(listOf(Fixtures.item("a", "1").replace("\"risingCount\":\"3\"", "\"risingCount\":\"$count\""))))
        assertThatThrownBy { provider().getDailySummary() }.isInstanceOf(IllegalStateException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["false", "true", "12", "{}", "[]", "null", "\" \""])
    fun `invalid identity values cannot become valid strings`(value: String) {
        listOf("code", "name").forEach { field ->
            val item = com.github.myeoungdev.marketticker.common.config.objectMapper.readTree(Fixtures.item("a", "1"))
                as com.fasterxml.jackson.databind.node.ObjectNode
            item.set<com.fasterxml.jackson.databind.JsonNode>(field,
                com.github.myeoungdev.marketticker.common.config.objectMapper.readTree(value))
            stub(Fixtures.page(listOf(item.toString())))
            assertThatThrownBy { provider().getDailySummary() }.isInstanceOf(Exception::class.java)
        }
    }

    @Test
    fun `legitimate empty completion is distinct from failure`() {
        stub(Fixtures.page())
        val summary = provider().getDailySummary()
        assertThat(summary.gainers).isEmpty()
        assertThat(summary.losers).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "missing"])
    fun `missing cursor rejects partial results`(cursor: String) {
        stub(Fixtures.page(listOf(Fixtures.item("a", "1")), true, cursor.takeUnless { it == "missing" }))
        assertThatThrownBy { provider().getDailySummary() }.isInstanceOf(IllegalStateException::class.java)
        assertThat(server.allServeEvents).hasSize(1)
    }

    @Test
    fun `cursor cycle stops before another request`() {
        stub(Fixtures.page(hasNext = true, cursor = "next"))
        stub(Fixtures.page(hasNext = true, cursor = "next"), "next")
        assertThatThrownBy { provider().getDailySummary() }.hasMessageContaining("repeated")
        assertThat(server.allServeEvents).hasSize(2)
    }

    @Test
    fun `five page cap prevents sixth request`() {
        stub(Fixtures.page(hasNext = true, cursor = "1"))
        (1..4).forEach { stub(Fixtures.page(hasNext = true, cursor = "${it + 1}"), "$it") }
        assertThatThrownBy { provider().getDailySummary() }.hasMessageContaining("five-page cap")
        assertThat(server.allServeEvents).hasSize(5)
    }

    @Test
    fun `completion on fifth page is permitted`() {
        stub(Fixtures.page(hasNext = true, cursor = "1"))
        (1..3).forEach { stub(Fixtures.page(hasNext = true, cursor = "${it + 1}"), "$it") }
        stub(Fixtures.page(listOf(Fixtures.item("last", "-5"))), "4")
        assertThat(provider().getDailySummary().losers.single().code).isEqualTo("last")
    }

    @Test
    fun `failed second page never returns first page ranking`() {
        stub(Fixtures.page(listOf(Fixtures.item("a", "10")), true, "next"))
        server.stubFor(get(urlPathEqualTo(path)).withQueryParam("cursor", equalTo("next"))
            .willReturn(aResponse().withStatus(503)))
        assertThatThrownBy { provider().getDailySummary() }.hasMessageContaining("503")
        assertThat(server.allServeEvents).hasSize(2)
    }

    @Test
    fun `request timeout settles caller and prevents later pages`() {
        stub(Fixtures.page(hasNext = true, cursor = "next"), delay = 600)
        assertThatThrownBy {
            provider(requestTimeout = Duration.ofMillis(100)).getDailySummary()
        }.isInstanceOf(HttpTimeoutException::class.java)
        Thread.sleep(650)
        assertThat(server.allServeEvents).hasSize(1)
    }

    @Test
    fun `request timeout also bounds a slowly streamed response body`() {
        server.stubFor(get(urlPathEqualTo(path)).willReturn(okJson(Fixtures.page(hasNext = true, cursor = "next"))
            .withChunkedDribbleDelay(5, 1000)))
        val started = System.nanoTime()
        assertThatThrownBy { provider(requestTimeout = Duration.ofMillis(400)).getDailySummary() }
            .isInstanceOf(Exception::class.java)
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(800))
        Thread.sleep(1100)
        assertThat(server.allServeEvents).hasSize(1)
    }

    @Test
    fun `whole chain budget bounds a blocked later request`() {
        stub(Fixtures.page(hasNext = true, cursor = "next"), delay = 150)
        stub(Fixtures.page(hasNext = true, cursor = "third"), "next", delay = 900)
        val started = System.nanoTime()
        assertThatThrownBy {
            provider(chainTimeout = Duration.ofMillis(600)).getDailySummary()
        }.isInstanceOf(HttpTimeoutException::class.java)
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2))
        Thread.sleep(1000)
        assertThat(server.allServeEvents).hasSize(2)
    }

    @Test
    fun `whole chain budget also bounds streamed bodies`() {
        server.stubFor(get(urlPathEqualTo(path)).willReturn(okJson(Fixtures.page(hasNext = true, cursor = "next"))
            .withChunkedDribbleDelay(5, 1000)))
        val started = System.nanoTime()
        assertThatThrownBy { provider(chainTimeout = Duration.ofMillis(400)).getDailySummary() }
            .isInstanceOf(Exception::class.java)
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(800))
        Thread.sleep(1100)
        assertThat(server.allServeEvents).hasSize(1)
    }

    @Test
    fun `completed final page cannot publish after deadline`() {
        stub(Fixtures.page(listOf(Fixtures.item("a", "1"))))
        var reads = 0
        val summaryProvider = NaverThemeSummaryProvider(client, clock,
            nanoTime = { if (reads++ < 2) 0L else Duration.ofSeconds(30).toNanos() })
        assertThatThrownBy { summaryProvider.getDailySummary() }.isInstanceOf(HttpTimeoutException::class.java)
        assertThat(server.allServeEvents).hasSize(1)
    }

    @Test
    fun `interrupting blocked send propagates interruption without later pages`() {
        stub(Fixtures.page(hasNext = true, cursor = "next"), delay = 1000)
        val result = CompletableFuture<Throwable>()
        val thread = Thread {
            try {
                provider().getDailySummary()
                result.complete(AssertionError("Unexpected success"))
            } catch (e: Throwable) {
                result.complete(e)
            }
        }
        thread.start()
        val waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (server.allServeEvents.isEmpty() && System.nanoTime() < waitUntil) Thread.sleep(10)
        thread.interrupt()
        assertThat(result.get(2, TimeUnit.SECONDS)).isInstanceOf(InterruptedException::class.java)
        thread.join(2000)
        assertThat(thread.isAlive).isFalse()
        Thread.sleep(1100)
        assertThat(server.allServeEvents).hasSize(1)
    }

    @Test
    fun `members use exact query preserve order and map markets and optional defaults`() {
        val memberPath = "/api/domestic/market/theme/566/stocklist"
        server.stubFor(get(urlPathEqualTo(memberPath)).willReturn(okJson(Fixtures.members)))
        val members = provider().getThemeMembers("566")
        assertThat(members.map { it.ticker.symbol }).containsExactly("005930", "042510", "000003", "000004")
        assertThat(members.map { it.ticker.marketType }).containsExactly(MarketType.KOSPI, MarketType.KOSDAQ,
            MarketType.KOREA, MarketType.KOREA)
        assertThat(members.first().price).isEqualTo(1200.0)
        assertThat(members.first().changeRate).isEqualTo(-2.5)
        assertThat(members.first().volume).isEqualTo(9000L)
        assertThat(members[1].changeRate).isEqualTo(3.0)
        assertThat(members.last().price).isNull()
        assertThat(members.last().changeRate).isNull()
        assertThat(members.last().volume).isNull()
        val requests = server.allServeEvents.map { it.request }
        assertThat(requests).hasSize(2)
        assertThat(requests.map { it.queryParameter("marketType").firstValue() })
            .containsExactlyInAnyOrder("KOSPI", "KOSDAQ")
        requests.forEach { request ->
            assertThat(request.queryParams.keys).containsExactlyInAnyOrder("marketType", "orderType", "startIdx", "pageSize")
            assertThat(request.queryParameter("orderType").firstValue()).isEqualTo("quantTop")
            assertThat(request.queryParameter("startIdx").firstValue()).isEqualTo("0")
            assertThat(request.queryParameter("pageSize").firstValue()).isEqualTo("20")
        }
    }

    @Test
    fun `members parse the current paged response shape from both markets`() {
        val memberPath = "/api/domestic/market/theme/566/stocklist"
        server.stubFor(get(urlPathEqualTo(memberPath)).withQueryParam("marketType", equalTo("KOSPI"))
            .willReturn(okJson("""{"hasNext":false,"items":[{"itemCode":"005930","itemName":"Samsung","krx":{"currentPrice":"71000","changeRate":"1.25","tradingVolume":"9000"}}],"cursor":null}""")))
        server.stubFor(get(urlPathEqualTo(memberPath)).withQueryParam("marketType", equalTo("KOSDAQ"))
            .willReturn(okJson("""{"hasNext":false,"items":[{"itemCode":"042510","itemName":"Raon","krx":{"currentPrice":"12000","changeRate":"-2.5","tradingVolume":"12000"}}],"cursor":null}""")))

        val members = provider().getThemeMembers("566")

        assertThat(members.map { it.ticker.symbol }).containsExactly("042510", "005930")
        assertThat(members.map { it.ticker.marketType }).containsExactly(MarketType.KOSDAQ, MarketType.KOSPI)
        assertThat(members.map { it.price }).containsExactly(12000.0, 71000.0)
        assertThat(members.map { it.changeRate }).containsExactly(-2.5, 1.25)
        assertThat(members.map { it.volume }).containsExactly(12000L, 9000L)
    }

    private fun provider(requestTimeout: Duration = Duration.ofSeconds(8), chainTimeout: Duration = Duration.ofSeconds(30)) =
        NaverThemeSummaryProvider(client, clock, requestTimeout, chainTimeout)

    private fun stub(body: String, cursor: String? = null, delay: Int = 0) {
        val mapping = get(urlPathEqualTo(path))
        if (cursor == null) mapping.withQueryParam("cursor", absent())
        else mapping.withQueryParam("cursor", equalTo(cursor))
        server.stubFor(mapping.willReturn(okJson(body).withFixedDelay(delay)))
    }
}
