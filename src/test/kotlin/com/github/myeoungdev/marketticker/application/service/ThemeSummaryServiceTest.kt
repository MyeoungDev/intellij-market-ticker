package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.model.theme.ThemeRefreshThrottledException
import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.application.provider.ThemeSummaryProvider
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeConstituent
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummary
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummarySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

@Timeout(10)
class ThemeSummaryServiceTest {
    private val initialTime = Instant.parse("2026-10-06T07:00:00Z")
    private val clock = MutableClock(initialTime)
    private val services = mutableListOf<ThemeSummaryService>()

    @AfterEach
    fun tearDown() = services.forEach { it.dispose() }

    @Test
    fun `registry reuses its theme provider`() {
        assertThat(DefaultDataSourceRegistry.themeSummaryProvider())
            .isSameAs(DefaultDataSourceRegistry.themeSummaryProvider())
    }

    @Test
    fun `construction and hidden visibility do not fetch or poll`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val service = service(provider)
        assertThat(service.summaryState().value).isNull()
        assertThat(service.summaryState().isLoading).isFalse()
        clock.advance(Duration.ofHours(1))
        assertThat(service.refreshIfStale(isVisible = false)).isNull()
        assertThat(provider.summaryCalls.get()).isZero()
        service.refreshIfStale(isVisible = true)
        assertThat(provider.summaryCalls.get()).isEqualTo(1)
    }

    @Test
    fun `five minute TTL expires at exact boundary using publication time`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val service = service(provider)
        val first = service.refresh()
        clock.advance(Duration.ofMinutes(5).minusNanos(1))
        assertThat(service.summaryState().isStale).isFalse()
        assertThat(service.refresh()).isSameAs(first)
        clock.advance(Duration.ofNanos(1))
        assertThat(service.summaryState().isStale).isTrue()
        val second = service.refresh()
        assertThat(provider.summaryCalls.get()).isEqualTo(2)
        assertThat(second.fetchedAt).isEqualTo(clock.instant())
        assertThat(service.summaryState().fetchedAt).isEqualTo(clock.instant())
        assertThat(service.summaryState().isStale).isFalse()
    }

    @Test
    fun `force refresh bypasses TTL`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val service = service(provider)
        service.refresh()
        clock.advance(Duration.ofSeconds(1))
        service.refresh(force = true)
        assertThat(provider.summaryCalls.get()).isEqualTo(2)
    }

    @Test
    fun `all visibility conditions gate stale refresh and visible reentry coalesces`() = runBlocking<Unit> {
        val dispatcher = QueuedDispatcher()
        val provider = FakeProvider(clock)
        val service = service(provider, dispatcher)
        val initial = async(start = CoroutineStart.UNDISPATCHED) { service.refreshIfStale(true) }
        dispatcher.runAll()
        val old = initial.await()
        clock.advance(Duration.ofMinutes(5))
        listOf(Triple(false, true, true), Triple(true, false, true), Triple(true, true, false)).forEach { (screen, theme, showing) ->
            assertThat(service.refreshIfStale(screen && theme && showing)).isSameAs(old)
        }
        assertThat(provider.summaryCalls.get()).isEqualTo(1)
        assertThat(service.summaryState().isStale).isTrue()
        val first = async(start = CoroutineStart.UNDISPATCHED) { service.refreshIfStale(true) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { service.refreshIfStale(true) }
        assertThat(service.summaryState().value).isSameAs(old)
        assertThat(service.summaryState().isLoading).isTrue()
        dispatcher.runAll()
        assertThat(first.await()).isSameAs(second.await())
        assertThat(provider.summaryCalls.get()).isEqualTo(2)
    }

    @Test
    fun `one worker publishes once regardless of delayed or cancelled consumers`() = runBlocking<Unit> {
        val dispatcher = QueuedDispatcher()
        val provider = FakeProvider(clock)
        val service = service(provider, dispatcher)
        val first = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { service.refresh(force = true) }
        dispatcher.runAll()
        assertThat(service.summaryState().fetchedAt).isEqualTo(initialTime)
        clock.advance(Duration.ofMinutes(4))
        first.cancelAndJoin()
        second.await()
        assertThat(service.summaryState().fetchedAt).isEqualTo(initialTime)
        assertThat(provider.summaryCalls.get()).isEqualTo(1)
        clock.advance(Duration.ofMinutes(1))
        assertThat(service.summaryState().isStale).isTrue()
    }

    @Test
    fun `cancelled consumer neither removes in flight nor cancels shared worker`() = runBlocking<Unit> {
        val dispatcher = QueuedDispatcher()
        val provider = FakeProvider(clock)
        val service = service(provider, dispatcher)
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        val surviving = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        cancelled.cancelAndJoin()
        assertThat(service.summaryState().isLoading).isTrue()
        val joining = async(start = CoroutineStart.UNDISPATCHED) { service.refresh(force = true) }
        dispatcher.runAll()
        assertThat(surviving.await()).isSameAs(joining.await())
        assertThat(provider.summaryCalls.get()).isEqualTo(1)
    }

    @Test
    fun `old consumers cannot clean up a newer forced refresh`() = runBlocking<Unit> {
        val dispatcher = QueuedDispatcher()
        val provider = FakeProvider(clock)
        val service = service(provider, dispatcher)
        val old = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        dispatcher.runAll()
        val fresh = async(start = CoroutineStart.UNDISPATCHED) { service.refresh(force = true) }
        old.cancelAndJoin()
        assertThat(service.summaryState().isLoading).isTrue()
        val joining = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        dispatcher.runAll()
        assertThat(fresh.await()).isSameAs(joining.await())
        assertThat(provider.summaryCalls.get()).isEqualTo(2)
    }

    @Test
    fun `first load failure is not empty success and can be retried`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val failure = IOException("offline")
        provider.summaryLoader = { throw failure }
        val service = service(provider)
        val result = runCatching { service.refresh() }
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java).hasMessage("offline")
        assertThat(service.summaryState().value).isNull()
        assertThat(service.summaryState().fetchedAt).isNull()
        assertThat(service.summaryState().error).isInstanceOf(IOException::class.java).hasMessage("offline")
        assertThat(service.summaryState().isLoading).isFalse()
        provider.summaryLoader = { snapshot(clock.instant(), empty = true) }
        val empty = service.refresh()
        assertThat(empty.gainers).isEmpty()
        assertThat(empty.losers).isEmpty()
        assertThat(service.summaryState().value).isNotNull()
        assertThat(service.summaryState().error).isNull()
    }

    @Test
    fun `failed forced refresh preserves last good and both successful timestamps as stale`() = runBlocking<Unit> {
        val dispatcher = QueuedDispatcher()
        val provider = FakeProvider(clock)
        val service = service(provider, dispatcher)
        val initial = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        dispatcher.runAll()
        val good = initial.await()
        clock.advance(Duration.ofSeconds(10))
        val failure = IOException("refresh failed")
        provider.summaryLoader = { throw failure }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refresh(true) } }
        assertThat(service.summaryState().isLoading).isTrue()
        assertThat(service.summaryState().isStale).isTrue()
        dispatcher.runAll()
        assertThat(refresh.await().exceptionOrNull()).isInstanceOf(IOException::class.java).hasMessage("refresh failed")
        val state = service.summaryState()
        assertThat(state.value).isSameAs(good)
        assertThat(state.value!!.fetchedAt).isEqualTo(initialTime)
        assertThat(state.fetchedAt).isEqualTo(initialTime)
        assertThat(state.error).isInstanceOf(IOException::class.java).hasMessage("refresh failed")
        assertThat(state.isStale).isTrue()
        assertThat(state.isLoading).isFalse()
        provider.summaryLoader = { snapshot(clock.instant()) }
        val retry = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        dispatcher.runAll()
        retry.await()
        assertThat(service.summaryState().fetchedAt).isEqualTo(clock.instant())
        assertThat(service.summaryState().isStale).isFalse()
        assertThat(service.summaryState().error).isNull()
    }

    @Test
    fun `cancellation is propagated rather than empty success`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        provider.summaryLoader = { throw CancellationException("provider cancelled") }
        val service = service(provider)
        assertThat(runCatching { service.refresh() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(service.summaryState().value).isNull()
        assertThat(service.summaryState().error).isInstanceOf(CancellationException::class.java)
        assertThat(service.summaryState().isLoading).isFalse()
    }

    @Test
    fun `manual refresh forces one call with exact thirty second cooldown`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val service = service(provider)
        service.refresh()
        assertThat(service.manualRefreshState().isAvailable).isTrue()
        service.refreshManually()
        assertThat(provider.summaryCalls.get()).isEqualTo(2)
        assertThat(service.manualRefreshState().retryAfter).isEqualTo(Duration.ofSeconds(30))
        clock.advance(Duration.ofSeconds(30).minusNanos(1))
        val blocked = runCatching { service.refreshManually() }.exceptionOrNull()
        assertThat(blocked).isInstanceOf(ThemeRefreshThrottledException::class.java)
        assertThat((blocked as ThemeRefreshThrottledException).retryAfter).isEqualTo(Duration.ofNanos(1))
        assertThat(provider.summaryCalls.get()).isEqualTo(2)
        clock.advance(Duration.ofNanos(1))
        assertThat(service.manualRefreshState().isAvailable).isTrue()
        service.refreshManually()
        assertThat(provider.summaryCalls.get()).isEqualTo(3)
    }

    @Test
    fun `manual request joins existing load without consuming cooldown`() = runBlocking<Unit> {
        val dispatcher = QueuedDispatcher()
        val provider = FakeProvider(clock)
        val service = service(provider, dispatcher)
        val auto = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        assertThat(service.manualRefreshState().isAvailable).isFalse()
        val manual = async(start = CoroutineStart.UNDISPATCHED) { service.refreshManually() }
        dispatcher.runAll()
        assertThat(auto.await()).isSameAs(manual.await())
        assertThat(service.manualRefreshState().retryAfter).isEqualTo(Duration.ZERO)
        val forced = async(start = CoroutineStart.UNDISPATCHED) { service.refreshManually() }
        assertThat(service.manualRefreshState().isAvailable).isFalse()
        dispatcher.runAll()
        forced.await()
        assertThat(provider.summaryCalls.get()).isEqualTo(2)
    }

    @Test
    fun `failed manual refresh is still throttled`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        provider.summaryLoader = { throw IOException("failed") }
        val service = service(provider)
        assertThat(runCatching { service.refreshManually() }.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(runCatching { service.refreshManually() }.exceptionOrNull()).isInstanceOf(ThemeRefreshThrottledException::class.java)
        assertThat(provider.summaryCalls.get()).isEqualTo(1)
    }

    @Test
    fun `member caches have independent keys TTL and failure state`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val service = service(provider)
        val summary = service.refresh()
        val a = service.refreshMembers("a")
        clock.advance(Duration.ofMinutes(2))
        val b = service.refreshMembers("b")
        clock.advance(Duration.ofMinutes(3))
        assertThat(service.memberState("a").isStale).isTrue()
        assertThat(service.memberState("b").isStale).isFalse()
        assertThat(service.refreshMembers("b")).isSameAs(b)
        val failure = IOException("a failed")
        provider.memberLoader = { throw failure }
        assertThat(runCatching { service.refreshMembers("a") }.exceptionOrNull()).isInstanceOf(IOException::class.java).hasMessage("a failed")
        assertThat(service.memberState("a").value).isSameAs(a)
        assertThat(service.memberState("a").fetchedAt).isEqualTo(initialTime)
        assertThat(service.memberState("a").error).isInstanceOf(IOException::class.java).hasMessage("a failed")
        assertThat(service.memberState("b").value).isSameAs(b)
        assertThat(service.memberState("b").error).isNull()
        assertThat(service.summaryState().value).isSameAs(summary)
        assertThat(service.summaryState().error).isNull()
        assertThat(provider.memberCalls["a"]!!.get()).isEqualTo(2)
        assertThat(provider.memberCalls["b"]!!.get()).isEqualTo(1)
        provider.memberLoader = { emptyList() }
        assertThat(service.refreshMembers("a")).isEmpty()
        assertThat(service.memberState("a").value).isNotNull()
        assertThat(service.memberState("a").isStale).isFalse()
    }

    @Test
    fun `summary failure never invalidates fresh members and member force bypasses only its own cache`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val service = service(provider)
        val a = service.refreshMembers("a")
        provider.summaryLoader = { throw IOException("summary failed") }
        runCatching { service.refresh() }
        assertThat(service.refreshMembers("a")).isSameAs(a)
        service.refreshMembers("a", force = true)
        assertThat(provider.memberCalls["a"]!!.get()).isEqualTo(2)
        assertThat(provider.summaryCalls.get()).isEqualTo(1)
        assertThat(service.memberState("a").error).isNull()
    }

    @Test
    fun `member concurrent requests coalesce independently of summary`() = runBlocking<Unit> {
        val dispatcher = QueuedDispatcher()
        val provider = FakeProvider(clock)
        val service = service(provider, dispatcher)
        val a = async(start = CoroutineStart.UNDISPATCHED) { service.refreshMembers("a") }
        val same = async(start = CoroutineStart.UNDISPATCHED) { service.refreshMembers("a", true) }
        val b = async(start = CoroutineStart.UNDISPATCHED) { service.refreshMembers("b") }
        val summary = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        a.cancelAndJoin()
        dispatcher.runAll()
        assertThat(same.await().single().ticker.symbol).isEqualTo("a")
        assertThat(b.await().single().ticker.symbol).isEqualTo("b")
        summary.await()
        assertThat(provider.memberCalls["a"]!!.get()).isEqualTo(1)
        assertThat(provider.memberCalls["b"]!!.get()).isEqualTo(1)
        assertThat(provider.summaryCalls.get()).isEqualTo(1)
    }

    @Test
    fun `dispose before workers begin settles all callers and clears every in flight entry`() = runBlocking<Unit> {
        val dispatcher = QueuedDispatcher()
        val provider = FakeProvider(clock)
        val service = service(provider, dispatcher)
        val summary = async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refresh() } }
        val memberA = async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refreshMembers("a") } }
        val memberB = async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refreshMembers("b") } }
        service.dispose()
        assertThat(summary.await().exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(memberA.await().exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(memberB.await().exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        dispatcher.runAll()
        assertThat(provider.summaryCalls.get()).isZero()
        assertThat(provider.memberCalls).isEmpty()
        assertThat(service.summaryState().isLoading).isFalse()
        assertThat(service.memberState("a").isLoading).isFalse()
        assertThat(service.memberState("b").isLoading).isFalse()
        assertThat(service.manualRefreshState().isAvailable).isFalse()
        assertThat(runCatching { service.refresh() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(runCatching { service.refreshMembers("a") }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        service.dispose()
    }

    @Test
    fun `dispose interrupts running blocking provider and settles waiter`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val entered = CompletableDeferred<Unit>()
        val interrupted = CompletableDeferred<Unit>()
        provider.summaryLoader = {
            entered.complete(Unit)
            try {
                Thread.sleep(Long.MAX_VALUE)
                error("Unexpected return")
            } catch (failure: InterruptedException) {
                interrupted.complete(Unit)
                throw failure
            }
        }
        val service = service(provider)
        val caller = async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refresh() } }
        withTimeout(2000) { entered.await() }
        service.dispose()
        assertThat(withTimeout(2000) { caller.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        withTimeout(2000) { interrupted.await() }
        assertThat(service.summaryState().value).isNull()
        assertThat(service.summaryState().isLoading).isFalse()
    }

    @Test
    fun `late noncooperative provider cannot publish after disposal`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val entered = CompletableDeferred<Unit>()
        val exited = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        provider.summaryLoader = {
            entered.complete(Unit)
            var waiting = true
            while (waiting) {
                try {
                    release.await()
                    waiting = false
                } catch (_: InterruptedException) {
                    // Deliberately hostile provider to prove publication is independently guarded.
                }
            }
            exited.complete(Unit)
            snapshot(clock.instant())
        }
        val service = service(provider)
        val caller = async(start = CoroutineStart.UNDISPATCHED) { runCatching { service.refresh() } }
        try {
            withTimeout(2000) { entered.await() }
            service.dispose()
            assertThat(caller.await().exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        } finally {
            release.countDown()
        }
        withTimeout(2000) { exited.await() }
        assertThat(service.summaryState().value).isNull()
        assertThat(service.summaryState().fetchedAt).isNull()
    }

    @Test
    fun `invalid member key never reaches provider`() = runBlocking<Unit> {
        val provider = FakeProvider(clock)
        val service = service(provider)
        assertThatThrownBy { runBlocking { service.refreshMembers(" ") } }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(provider.memberCalls).isEmpty()
    }

    private fun service(provider: ThemeSummaryProvider, dispatcher: CoroutineDispatcher = Dispatchers.IO) =
        ThemeSummaryService(provider, clock, dispatcher).also { services.add(it) }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        fun runAll() {
            while (true) (queue.poll() ?: return).run()
        }
    }

    private class MutableClock(initial: Instant) : Clock() {
        private val now = AtomicReference(initial)
        override fun instant(): Instant = now.get()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
        fun advance(duration: Duration) { now.updateAndGet { it.plus(duration) } }
    }

    private class FakeProvider(private val clock: Clock) : ThemeSummaryProvider {
        val summaryCalls = AtomicInteger()
        val memberCalls = ConcurrentHashMap<String, AtomicInteger>()
        var summaryLoader: () -> ThemeSummarySnapshot = { snapshot(clock.instant()) }
        var memberLoader: (String) -> List<ThemeConstituent> = { code ->
            listOf(ThemeConstituent(Ticker(code, code, code, MarketType.KOREA, "KOR", "Korea")))
        }
        override fun getDailySummary(): ThemeSummarySnapshot {
            summaryCalls.incrementAndGet()
            return summaryLoader()
        }
        override fun getThemeMembers(themeCode: String): List<ThemeConstituent> {
            memberCalls.computeIfAbsent(themeCode) { AtomicInteger() }.incrementAndGet()
            return memberLoader(themeCode)
        }
    }

    companion object {
        private fun snapshot(time: Instant, empty: Boolean = false) = ThemeSummarySnapshot(
            if (empty) emptyList() else listOf(ThemeSummary("1", "Theme", 1.0, 1, 0, 0, time.minusSeconds(1), emptyList())),
            emptyList(), time
        )
    }
}
