package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.provider.ThemeSummaryProvider
import com.github.myeoungdev.marketticker.application.service.ThemeSummaryService
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeConstituent
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummarySnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.CoroutineContext

class ThemeMemberInteractionTest {
    private val dispatcher = QueuedDispatcher()
    private val clock = MutableClock()
    private val provider = FakeProvider()
    private val service = ThemeSummaryService(provider, clock, dispatcher)
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private var renders = 0
    private val interaction = ThemeMemberInteraction(service, scope, dispatcher) { ++renders }

    @AfterEach
    fun tearDown() {
        interaction.dispose()
        scope.cancel()
        service.dispose()
        dispatcher.runAll()
    }

    @Test
    fun `members load lazily with provider volume order and ticker markets preserved`() {
        provider.loader = { listOf(member("high", MarketType.KOSPI), member("low", MarketType.KOSDAQ)) }
        assertThat(provider.calls).isEmpty()
        interaction.toggle("a")
        assertThat(interaction.expandedThemeCode).isNull()
        interaction.setVisible(true)
        interaction.toggle("a")
        assertThat(interaction.expandedThemeCode).isEqualTo("a")
        assertThat(interaction.state.isLoading).isTrue()
        assertThat(interaction.state.value).isNull()
        dispatcher.runAll()
        assertThat(interaction.state).isEqualTo(service.memberState("a"))
        assertThat(interaction.state.value!!.map { it.ticker.symbol }).containsExactly("high", "low")
        assertThat(interaction.state.value!!.map { it.ticker.marketType }).containsExactly(MarketType.KOSPI, MarketType.KOSDAQ)
        assertThat(interaction.state.isLoading).isFalse()
        assertThat(interaction.state.error).isNull()
    }

    @Test
    fun `same theme collapses and another theme replaces the single selection`() {
        interaction.setVisible(true)
        interaction.toggle("a")
        dispatcher.runAll()
        interaction.toggle("a")
        assertThat(interaction.expandedThemeCode).isNull()
        assertThat(interaction.state.value).isNull()
        interaction.toggle("a")
        dispatcher.runAll()
        assertThat(provider.calls).containsExactly("a")
        interaction.toggle("b")
        assertThat(interaction.expandedThemeCode).isEqualTo("b")
        assertThat(interaction.state.value).isNull()
        dispatcher.runAll()
        assertThat(interaction.state.value!!.single().ticker.symbol).isEqualTo("b")
        assertThat(provider.calls).containsExactly("a", "b")
    }

    @Test
    fun `empty success is distinct from first load error and retry recovers`() {
        interaction.setVisible(true)
        provider.loader = { throw IOException("members offline") }
        interaction.toggle("a")
        dispatcher.runAll()
        assertThat(interaction.state.error).isInstanceOf(IOException::class.java)
        assertThat(interaction.state.value).isNull()
        assertThat(interaction.state.isLoading).isFalse()
        provider.loader = { emptyList() }
        interaction.refresh(force = true)
        dispatcher.runAll()
        assertThat(interaction.state.value).isEmpty()
        assertThat(interaction.state.fetchedAt).isEqualTo(clock.instant())
        assertThat(interaction.state.error).isNull()
        assertThat(interaction.state.isStale).isFalse()
    }

    @Test
    fun `member refresh failure keeps last good timestamp and cannot invalidate summary`() = runBlocking<Unit> {
        val summary = async(start = CoroutineStart.UNDISPATCHED) { service.refresh() }
        dispatcher.runAll()
        val snapshot = summary.await()
        interaction.setVisible(true)
        interaction.toggle("a")
        dispatcher.runAll()
        val good = interaction.state.value
        val fetchedAt = interaction.state.fetchedAt
        clock.advance(Duration.ofSeconds(30))
        provider.loader = { throw IOException("member refresh failed") }
        interaction.refresh(force = true)
        assertThat(interaction.state.value).isSameAs(good)
        assertThat(interaction.state.isLoading).isTrue()
        assertThat(interaction.state.isStale).isTrue()
        dispatcher.runAll()
        assertThat(interaction.state.value).isSameAs(good)
        assertThat(interaction.state.fetchedAt).isEqualTo(fetchedAt)
        assertThat(interaction.state.error).isInstanceOf(IOException::class.java)
        assertThat(interaction.state.isStale).isTrue()
        assertThat(service.summaryState().value).isSameAs(snapshot)
        assertThat(service.summaryState().error).isNull()
        provider.loader = { listOf(member("new")) }
        interaction.refresh(force = true)
        dispatcher.runAll()
        assertThat(interaction.state.value!!.single().ticker.symbol).isEqualTo("new")
        assertThat(interaction.state.fetchedAt).isEqualTo(clock.instant())
        assertThat(interaction.state.isStale).isFalse()
    }

    @Test
    fun `older theme completion cannot overwrite the newer selected theme`() {
        interaction.setVisible(true)
        interaction.toggle("a")
        interaction.toggle("b")
        dispatcher.runLast() // Complete b before a's service-owned worker.
        dispatcher.runLast() // Resume b's UI consumer.
        assertThat(interaction.state.value!!.single().ticker.symbol).isEqualTo("b")
        val selectedState = interaction.state
        val selectedRenders = renders
        dispatcher.runAll()
        assertThat(service.memberState("a").value).isNotNull()
        assertThat(interaction.expandedThemeCode).isEqualTo("b")
        assertThat(interaction.state).isSameAs(selectedState)
        assertThat(renders).isEqualTo(selectedRenders)
    }

    @Test
    fun `collapse while loading ignores completion but allows cached re-expansion`() {
        interaction.setVisible(true)
        interaction.toggle("a")
        interaction.toggle("a")
        val collapsedRenders = renders
        dispatcher.runAll()
        assertThat(renders).isEqualTo(collapsedRenders)
        assertThat(interaction.expandedThemeCode).isNull()
        assertThat(interaction.state.value).isNull()
        interaction.toggle("a")
        dispatcher.runAll()
        assertThat(interaction.state.value).isEqualTo(service.memberState("a").value)
        assertThat(provider.calls).containsExactly("a")
    }

    @Test
    fun `switch away and back to same theme joins only current generation`() {
        interaction.setVisible(true)
        interaction.toggle("a")
        interaction.toggle("b")
        interaction.toggle("a")
        dispatcher.runAll()
        assertThat(interaction.expandedThemeCode).isEqualTo("a")
        assertThat(interaction.state.value!!.single().ticker.symbol).isEqualTo("a")
        assertThat(provider.calls.count { it == "a" }).isEqualTo(1)
    }

    @Test
    fun `hidden completion cannot render and expired round trip refreshes once retaining expansion`() {
        interaction.setVisible(true)
        interaction.toggle("a")
        dispatcher.runAll()
        val good = interaction.state.value
        val fetchedAt = interaction.state.fetchedAt
        interaction.refresh(force = true)
        interaction.setVisible(false)
        val hiddenRenders = renders
        dispatcher.runAll()
        assertThat(renders).isEqualTo(hiddenRenders)
        clock.advance(Duration.ofMinutes(5))
        interaction.refresh()
        interaction.refresh(force = true)
        assertThat(provider.calls).containsExactly("a", "a")
        interaction.setVisible(true)
        interaction.refresh()
        interaction.refresh() // Duplicate visibility/manual consumer joins the same service load.
        assertThat(interaction.expandedThemeCode).isEqualTo("a")
        assertThat(interaction.state.value).isEqualTo(good)
        assertThat(interaction.state.fetchedAt).isEqualTo(fetchedAt)
        assertThat(interaction.state.isStale).isTrue()
        assertThat(interaction.state.isLoading).isTrue()
        dispatcher.runAll()
        assertThat(provider.calls).containsExactly("a", "a", "a")
        assertThat(interaction.state.isStale).isFalse()
    }

    @Test
    fun `disposed view ignores late completion and future actions`() {
        interaction.setVisible(true)
        interaction.toggle("a")
        interaction.dispose()
        val disposedRenders = renders
        dispatcher.runAll()
        assertThat(service.memberState("a").value).isNotNull()
        assertThat(interaction.state.value).isNull()
        interaction.setVisible(true)
        interaction.toggle("b")
        interaction.refresh(force = true)
        dispatcher.runAll()
        assertThat(renders).isEqualTo(disposedRenders)
        assertThat(provider.calls).containsExactly("a")
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = mutableListOf<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        fun runLast() { queue.removeAt(queue.lastIndex).run() }
        fun runAll() { while (queue.isNotEmpty()) queue.removeAt(0).run() }
    }

    private class MutableClock : Clock() {
        private var now = Instant.parse("2026-10-06T08:00:00Z")
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        fun advance(duration: Duration) { now = now.plus(duration) }
    }

    private class FakeProvider : ThemeSummaryProvider {
        val calls = mutableListOf<String>()
        var loader: (String) -> List<ThemeConstituent> = { listOf(member(it)) }
        override fun getThemeMembers(themeCode: String): List<ThemeConstituent> {
            calls.add(themeCode)
            return loader(themeCode)
        }
        override fun getDailySummary() = ThemeSummarySnapshot(emptyList(), emptyList(), Instant.EPOCH)
    }

    companion object {
        private fun member(code: String, market: MarketType = MarketType.KOREA) =
            ThemeConstituent(Ticker(code, code, code, market, "KOR", "Korea"))
    }
}
