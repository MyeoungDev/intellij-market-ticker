package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.fixtures.naver.NaverThemeFixtures as Fixtures
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverClient
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverThemeSummaryProvider
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.net.http.HttpTimeoutException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors

class ThemeSummaryServiceTransportTest {
    @Test
    fun `request timeout settles shared callers and retains last good without later pages or publication`(): Unit =
        withTransport(requestTimeout = Duration.ofSeconds(1), chainTimeout = Duration.ofSeconds(6)) { transport ->
            val service = transport.service
            transport.seedSummary()
            val lastGood = service.summaryState()
            transport.stubPage(Fixtures.page(listOf(Fixtures.item("late", "99")), true, "next"), delayed = true)
            transport.stubPage(Fixtures.page(listOf(Fixtures.item("later", "-99"))), cursor = "next")

            val callers = List(2) {
                async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refresh(force = true) } }
            }
            transport.awaitDelayedRequest()
            assertThat(service.summaryState()).isEqualTo(lastGood.copy(isLoading = true, isStale = true))

            val failures = withTimeout(3_000) { callers.awaitAll() }.map { it.exceptionOrNull() }
            failures.forEach { assertThat(it).isInstanceOf(HttpTimeoutException::class.java) }
            assertThat(failures[1]?.message).isEqualTo(failures[0]?.message)
            transport.awaitWorkerIdle()
            val failed = service.summaryState()
            assertThat(failed.value).isSameAs(lastGood.value)
            assertThat(failed.fetchedAt).isEqualTo(lastGood.fetchedAt)
            assertThat(failed.error).isInstanceOf(HttpTimeoutException::class.java)
            assertThat(failed.error?.message).isEqualTo(failures[0]?.message)
            assertThat(failed.isLoading).isFalse()
            assertThat(failed.isStale).isTrue()

            transport.awaitLateResponseWindow()
            assertThat(service.summaryState()).isEqualTo(failed)
            transport.server.verify(1, getRequestedFor(urlPathEqualTo(RANK_PATH)))
            transport.server.verify(0, getRequestedFor(urlPathEqualTo(RANK_PATH)).withQueryParam("cursor", equalTo("next")))
        }

    @Test
    fun `whole chain deadline settles shared callers on later page without partial or late publication`(): Unit =
        withTransport(requestTimeout = Duration.ofSeconds(5), chainTimeout = Duration.ofSeconds(2)) { transport ->
            val service = transport.service
            transport.seedSummary()
            val lastGood = service.summaryState()
            transport.stubPage(Fixtures.page(listOf(Fixtures.item("partial", "88")), true, "next"), delayMillis = 150)
            transport.stubPage(Fixtures.page(listOf(Fixtures.item("late", "-99")), true, "third"),
                cursor = "next", delayed = true)
            transport.stubPage(Fixtures.page(listOf(Fixtures.item("later", "-100"))), cursor = "third")

            val callers = List(2) {
                async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refresh(force = true) } }
            }
            transport.awaitDelayedRequest()
            assertThat(service.summaryState()).isEqualTo(lastGood.copy(isLoading = true, isStale = true))

            // Three seconds is below the five-second per-request budget: only the chain bound can settle this send.
            val failures = withTimeout(3_000) { callers.awaitAll() }.map { it.exceptionOrNull() }
            failures.forEach { assertThat(it).isInstanceOf(HttpTimeoutException::class.java) }
            assertThat(failures[1]?.message).isEqualTo(failures[0]?.message)
            transport.awaitWorkerIdle()
            val failed = service.summaryState()
            assertThat(failed.value).isSameAs(lastGood.value)
            assertThat(failed.fetchedAt).isEqualTo(lastGood.fetchedAt)
            assertThat(failed.error).isInstanceOf(HttpTimeoutException::class.java)
            assertThat(failed.error?.message).isEqualTo(failures[0]?.message)
            assertThat(failed.isLoading).isFalse()
            assertThat(failed.isStale).isTrue()

            transport.awaitLateResponseWindow()
            assertThat(service.summaryState()).isEqualTo(failed)
            transport.server.verify(2, getRequestedFor(urlPathEqualTo(RANK_PATH)))
            transport.server.verify(1, getRequestedFor(urlPathEqualTo(RANK_PATH)).withQueryParam("cursor", equalTo("next")))
            transport.server.verify(0, getRequestedFor(urlPathEqualTo(RANK_PATH)).withQueryParam("cursor", equalTo("third")))
        }

    @Test
    fun `dispose interrupts blocked summary send and settles shared callers without later pages or cache publication`(): Unit =
        withTransport { transport ->
            val service = transport.service
            transport.seedSummary()
            val lastGood = service.summaryState()
            transport.stubPage(Fixtures.page(listOf(Fixtures.item("late", "99")), true, "next"), delayed = true)
            transport.stubPage(Fixtures.page(listOf(Fixtures.item("later", "-99"))), cursor = "next")

            val callers = List(2) {
                async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refresh(force = true) } }
            }
            transport.awaitDelayedRequest()
            assertThat(service.summaryState().isLoading).isTrue()
            service.dispose()

            val failures = withTimeout(1_000) { callers.awaitAll() }.map { it.exceptionOrNull() }
            failures.forEach { assertThat(it).isInstanceOf(CancellationException::class.java) }
            transport.awaitWorkerIdle()
            val disposed = service.summaryState()
            assertThat(disposed.value).isSameAs(lastGood.value)
            assertThat(disposed.fetchedAt).isEqualTo(lastGood.fetchedAt)
            assertThat(disposed.isLoading).isFalse()

            transport.awaitLateResponseWindow()
            assertThat(service.summaryState()).isEqualTo(disposed)
            assertThat(runCatching { service.refresh(force = true) }.exceptionOrNull())
                .isInstanceOf(CancellationException::class.java)
            transport.server.verify(1, getRequestedFor(urlPathEqualTo(RANK_PATH)))
            transport.server.verify(0, getRequestedFor(urlPathEqualTo(RANK_PATH)).withQueryParam("cursor", equalTo("next")))
        }

    @Test
    fun `dispose interrupts blocked member send without changing member or summary caches`(): Unit =
        withTransport { transport ->
            val service = transport.service
            transport.seedSummary()
            val summary = service.summaryState()
            transport.server.stubFor(get(urlPathEqualTo(MEMBER_PATH)).willReturn(okJson(Fixtures.members)))
            withTimeout(3_000) { service.refreshMembers("566") }
            val lastGood = service.memberState("566")
            transport.server.resetRequests()
            transport.server.stubFor(get(urlPathEqualTo(MEMBER_PATH))
                .withServeEventListener(transport.events.getName(), Parameters.empty())
                .willReturn(okJson("[]").withFixedDelay(DELAY_MILLIS)))

            val callers = List(2) {
                async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refreshMembers("566", force = true) } }
            }
            transport.awaitDelayedRequest()
            assertThat(service.memberState("566")).isEqualTo(lastGood.copy(isLoading = true, isStale = true))
            service.dispose()

            val failures = withTimeout(1_000) { callers.awaitAll() }.map { it.exceptionOrNull() }
            failures.forEach { assertThat(it).isInstanceOf(CancellationException::class.java) }
            transport.awaitWorkerIdle()
            val disposed = service.memberState("566")
            assertThat(disposed.value).isSameAs(lastGood.value)
            assertThat(disposed.fetchedAt).isEqualTo(lastGood.fetchedAt)
            assertThat(disposed.isLoading).isFalse()
            assertThat(service.summaryState()).isEqualTo(summary)

            transport.awaitLateResponseWindow()
            assertThat(service.memberState("566")).isEqualTo(disposed)
            assertThat(service.summaryState()).isEqualTo(summary)
            assertThat(runCatching { service.refreshMembers("566", force = true) }.exceptionOrNull())
                .isInstanceOf(CancellationException::class.java)
            transport.server.verify(1, getRequestedFor(urlPathEqualTo(MEMBER_PATH)))
            transport.server.verify(0, getRequestedFor(urlPathEqualTo(RANK_PATH)))
        }

    private fun withTransport(
        requestTimeout: Duration = Duration.ofSeconds(8),
        chainTimeout: Duration = Duration.ofSeconds(30),
        test: suspend CoroutineScope.(Transport) -> Unit
    ) {
        val events = DelayedRequestEvents()
        val server = WireMockServer(wireMockConfig().dynamicPort().extensions(events))
        server.start()
        val ioDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val clock = Clock.fixed(Instant.parse("2026-10-06T07:00:00Z"), ZoneOffset.UTC)
        val client = NaverClient(
            themeRankingsUrl = "${server.baseUrl()}$RANK_PATH",
            themeMembersUrl = "${server.baseUrl()}/api/domestic/market/theme",
            client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build())
        val service = ThemeSummaryService(NaverThemeSummaryProvider(client, clock, requestTimeout, chainTimeout),
            clock, ioDispatcher)
        try {
            runBlocking { test(Transport(server, service, events, ioDispatcher)) }
        } finally {
            service.dispose()
            ioDispatcher.close()
            server.stop()
        }
    }

    private class Transport(
        val server: WireMockServer,
        val service: ThemeSummaryService,
        val events: DelayedRequestEvents,
        private val ioDispatcher: kotlinx.coroutines.ExecutorCoroutineDispatcher
    ) {
        suspend fun seedSummary() {
            stubPage(Fixtures.page(listOf(Fixtures.item("good-up", "3"), Fixtures.item("good-down", "-2"))))
            val snapshot = withTimeout(3_000) { service.refresh() }
            assertThat(snapshot.gainers.single().code).isEqualTo("good-up")
            assertThat(snapshot.losers.single().code).isEqualTo("good-down")
            assertThat(service.summaryState().isStale).isFalse()
            server.resetRequests()
        }

        fun stubPage(body: String, cursor: String? = null, delayed: Boolean = false, delayMillis: Int = 0) {
            val mapping = get(urlPathEqualTo(RANK_PATH))
                .withQueryParam("cursor", cursor?.let { equalTo(it) } ?: absent())
            if (delayed) mapping.withServeEventListener(events.getName(), Parameters.empty())
            server.stubFor(mapping.willReturn(okJson(body).withFixedDelay(if (delayed) DELAY_MILLIS else delayMillis)))
        }

        suspend fun awaitDelayedRequest() {
            // WireMock signals before sending the delayed response; never guess whether the blocking send has begun.
            withTimeout(2_500) { events.received.await() }
        }

        suspend fun awaitWorkerIdle() {
            // This single-thread dispatcher cannot run the probe while HttpClient.send still occupies its worker.
            withTimeout(1_000) { withContext(ioDispatcher) { Unit } }
        }

        suspend fun awaitLateResponseWindow() {
            // Observe beyond the delayed successful response, not just the immediate exceptional completion.
            delay(DELAY_MILLIS.toLong() + 250)
            awaitWorkerIdle()
        }
    }

    private class DelayedRequestEvents : ServeEventListener {
        val received = CompletableDeferred<Unit>()

        override fun getName() = "theme-service-delayed-request"

        override fun applyGlobally() = false

        override fun beforeResponseSent(serveEvent: ServeEvent, parameters: Parameters) {
            received.complete(Unit)
        }
    }

    companion object {
        private const val RANK_PATH = "/api/stockSecurity/rankings/v2/domestic/themes"
        private const val MEMBER_PATH = "/api/domestic/market/theme/566/stocklist"
        private const val DELAY_MILLIS = 3_500
    }
}
