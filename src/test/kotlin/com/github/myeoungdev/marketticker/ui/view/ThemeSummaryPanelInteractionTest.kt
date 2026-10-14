package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.provider.ThemeSummaryProvider
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.ThemeSummaryService
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeConstituent
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummary
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummarySnapshot
import com.intellij.mock.MockApplication
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.io.IOException
import java.awt.event.ActionEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

class ThemeSummaryPanelInteractionTest {
    private val disposable = Disposer.newDisposable()
    private val dispatcher = QueuedDispatcher()
    private val clock = MutableClock()
    private val provider = FakeProvider(clock)
    private val service = ThemeSummaryService(provider, clock, dispatcher)
    private lateinit var panel: ThemeSummaryPanel
    private lateinit var root: JPanel
    private val modes = JTabbedPane()
    private val mainModes = JTabbedPane()
    private val stocks = JPanel()
    private val addedToWatchlist = mutableListOf<Ticker>()

    @BeforeEach
    fun setUp() {
        val app = MockApplication.setUp(disposable)
        app.registerService(AppSettingsService::class.java, AppSettingsService().apply {
            loadState(AppSettingsService.State(uiLanguage = AppSettingsService.UiLanguage.EN.name))
        })
        app.registerService(LocalizationService::class.java, LocalizationService())
        onEdt {
            panel = ThemeSummaryPanel(service, dispatcher, dispatcher, addedToWatchlist::add)
            modes.addTab("Rankings", JPanel())
            modes.addTab("Themes", panel)
            modes.selectedComponent = panel
            mainModes.addTab("Stocks", stocks)
            mainModes.addTab("Screener", modes)
            mainModes.selectedComponent = modes
            root = object : JPanel() {
                override fun isShowing(): Boolean = true
            }.apply { add(mainModes) }
            root.addNotify()
            panel.syncVisibility()
            dispatcher.runAll()
        }
    }

    @AfterEach
    fun tearDown() {
        onEdt {
            if (::panel.isInitialized) panel.dispose()
            if (::root.isInitialized) root.removeNotify()
            service.dispose()
            dispatcher.runAll()
        }
        Disposer.dispose(disposable)
    }

    @Test
    fun `theme action expands its constituents inline and collapses them`() = onEdt {
        assertThat(descendants(themeCard("a")).filterIsInstance<JButton>())
            .noneMatch { it.text == "Show stocks" || it.name == "theme-toggle-a" }
        assertThat(themeTitleButton("a").text).isEqualTo("Alpha  ▾")
        assertThat(themeTitleButton("a").accessibleContext.accessibleName)
            .isEqualTo("Expand Alpha constituents")
        val themeCardBeforeToggle = themeCard("a")
        themeToggleButton("a").doClick(0)
        assertThat(themeToggleButton("a").text).isEqualTo("Alpha  ▴")
        assertThat(themeTitleButton("a").text).isEqualTo("Alpha  ▴")
        assertThat(themeCard("a")).isSameAs(themeCardBeforeToggle)
        assertThat(labels()).contains("Up 1 · Flat 0 · Down 0", "Loading members...")
        assertThat(labels().count { it == "Loading members..." }).isEqualTo(1)
        dispatcher.runAll()
        assertThat(themeCard("a")).isSameAs(themeCardBeforeToggle)
        assertThat(descendants(themeCard("a")).filterIsInstance<javax.swing.JTable>()).hasSize(1)
        assertThat(memberTable().rowCount).isEqualTo(1)
        assertThat(memberTable().getValueAt(0, 0)).isEqualTo("a member")
        assertThat(labels()).noneMatch { it.contains("상세 정보") || it.contains("details in Stocks") }
        themeToggleButton("a").doClick(0)
        assertThat(themeToggleButton("a").text).isEqualTo("Alpha  ▾")
        assertThat(themeTitleButton("a").text).isEqualTo("Alpha  ▾")
        assertThat(descendants(themeCard("a")).filterIsInstance<javax.swing.JTable>()).isEmpty()
        assertThat(provider.memberCalls).containsExactly("a")
    }

    @Test
    fun `overview cards are bounded by their content height`() = onEdt {
        val cards = descendants(panel).filterIsInstance<JPanel>()
            .filter { it.name?.startsWith("theme-summary-card-") == true }
        assertThat(cards).hasSize(2)
        assertThat(cards.map { it.preferredSize.height }.distinct()).hasSize(1)
        cards.forEach { card ->
            assertThat(card.maximumSize.height).isEqualTo(card.preferredSize.height)
            assertThat(card.maximumSize.height).isLessThan(300)
        }
    }

    @Test
    fun `member double click adds to watchlist instead of navigating away`() = onEdt {
        themeToggleButton("a").doClick(0)
        dispatcher.runAll()
        val member = service.memberState("a").value!!.single().ticker
        val stocksTabBefore = mainModes.selectedComponent
        memberTable().setRowSelectionInterval(0, 0)
        val cell = memberTable().getCellRect(0, 0, true)
        memberTable().dispatchEvent(java.awt.event.MouseEvent(
            memberTable(), java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
            cell.x + cell.width / 2, cell.y + cell.height / 2,
            cell.x + cell.width / 2, cell.y + cell.height / 2, 2, false,
            java.awt.event.MouseEvent.BUTTON1
        ))
        assertThat(addedToWatchlist).containsExactly(member)
        assertThat(mainModes.selectedComponent).isSameAs(stocksTabBefore)
        assertThat(modes.selectedComponent).isSameAs(panel)
        assertThat(themeToggleButton("a").text).isEqualTo("Alpha  ▴")
        assertThat(themeToggleButton("a").text).isEqualTo("Alpha  ▴")
        assertThat(memberTable().getValueAt(0, 0)).isEqualTo("a member")
        assertThat(labels()).noneMatch { it.contains("조회 완료") || it.contains("Fetched") || it.contains("네이버 기준") }
    }

    @Test
    fun `summary refresh preserves expanded members on member failure`() = onEdt {
        themeToggleButton("a").doClick(0)
        dispatcher.runAll()
        val fetchedAt = service.memberState("a").fetchedAt
        provider.memberFailure = IOException("members failed")
        button("Refresh").doClick(0)
        dispatcher.runAll()
        assertThat(provider.summaryCalls).isEqualTo(2)
        assertThat(provider.memberCalls).containsExactly("a", "a")
        assertThat(service.summaryState().error).isNull()
        assertThat(service.memberState("a").fetchedAt).isEqualTo(fetchedAt)
        assertThat(themeToggleButton("a").text).isEqualTo("Alpha  ▴")
        assertThat(memberTable().getValueAt(0, 0)).isEqualTo("a member")
        assertThat(buttons()).contains("Retry members")
        assertThat(labels()).contains("Member refresh failed · showing stale data")
        provider.memberFailure = null
        button("Retry members").doClick(0)
        dispatcher.runAll()
        assertThat(service.memberState("a").error).isNull()
        assertThat(provider.summaryCalls).isEqualTo(2)
    }

    @Test
    fun `empty result and first load error have distinct actionable UI`() = onEdt {
        provider.emptyMembers = true
        themeToggleButton("a").doClick(0)
        dispatcher.runAll()
        assertThat(labels()).contains("No members available.")
        themeToggleButton("a").doClick(0)
        provider.memberFailure = IOException("offline")
        themeToggleButton("b").doClick(0)
        dispatcher.runAll()
        assertThat(visibleLabels()).contains("Could not load members.")
            .doesNotContain("No members available.")
        assertThat(buttons()).contains("Retry members")
    }

    @Test
    fun `member table keeps provider order and displays at most twenty rows`() = onEdt {
        provider.memberCount = 25
        themeToggleButton("a").doClick(0)
        dispatcher.runAll()
        assertThat(memberTable().rowCount).isEqualTo(20)
        assertThat((0 until memberTable().rowCount).map { memberTable().getValueAt(it, 0) })
            .containsExactlyElementsOf((1..20).map { "a member $it" })
        val tableScroll = descendants(panel).filterIsInstance<com.intellij.ui.components.JBScrollPane>()
            .single { it.name == "theme-member-table-scroll" }
        assertThat(tableScroll.horizontalScrollBarPolicy)
            .isEqualTo(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
        assertThat(tableScroll.preferredSize.height)
            .isLessThan(memberTable().tableHeader.preferredSize.height + memberTable().rowHeight * 6)
        assertThat(buttons().none { it.startsWith("a member") }).isTrue()
    }

    @Test
    fun `hidden late completion cannot repaint retained member content`() = onEdt {
        themeTitleButton("a").doClick(0)
        mainModes.selectedComponent = stocks
        panel.syncVisibility()
        val hiddenLabels = labels()
        val hiddenButtons = buttons()
        dispatcher.runAll()
        assertThat(service.memberState("a").value).isNotNull()
        assertThat(labels()).isEqualTo(hiddenLabels)
        assertThat(buttons()).isEqualTo(hiddenButtons)
        mainModes.selectedComponent = modes
        panel.syncVisibility()
        dispatcher.runAll()
        assertThat(themeToggleButton("a").text).isEqualTo("Alpha  ▴")
        assertThat(memberTable().getValueAt(0, 0)).isEqualTo("a member")
        assertThat(provider.memberCalls).containsExactly("a")
    }

    @Test
    fun `late member completion cannot replace a newly selected theme and disposed panel ignores results`() = onEdt {
        themeToggleButton("a").doClick(0)
        themeToggleButton("a").doClick(0)
        themeToggleButton("b").doClick(0)
        dispatcher.runLast()
        dispatcher.runLast()
        assertThat(themeToggleButton("b").text).isEqualTo("Beta  ▴")
        assertThat(memberTable().getValueAt(0, 0)).isEqualTo("b member")
        dispatcher.runAll()
        assertThat(memberTable().getValueAt(0, 0)).isEqualTo("b member")
        provider.memberFailure = IOException("late failure")
        button("Refresh").doClick(0)
        val disposedLabels = labels()
        val disposedButtons = buttons()
        panel.dispose()
        dispatcher.runAll()
        assertThat(labels()).isEqualTo(disposedLabels)
        assertThat(buttons()).isEqualTo(disposedButtons)
    }

    private fun button(text: String): JButton = descendants(panel).filterIsInstance<JButton>().single { it.text == text }
    private fun themeToggleButton(themeCode: String): JButton = themeTitleButton(themeCode)
    private fun themeTitleButton(themeCode: String): JButton = descendants(panel).filterIsInstance<JButton>()
        .single { it.name == "theme-title-$themeCode" }
    private fun themeCard(themeCode: String): JPanel = descendants(panel).filterIsInstance<JPanel>()
        .single { it.name == "theme-summary-card-$themeCode" }
    private fun memberTable(): javax.swing.JTable = descendants(panel).filterIsInstance<javax.swing.JTable>().single()
    private fun buttons(): List<String> = descendants(panel).filterIsInstance<JButton>().map { it.text }
    private fun labels(): List<String> = descendants(panel).filterIsInstance<JLabel>().map { it.text }
    private fun visibleLabels(): List<String> = descendants(panel).filterIsInstance<JLabel>()
        .filter { it.isShowing }.map { it.text }
    private fun descendants(component: Component): List<Component> = listOf(component) +
        (component as? Container)?.components.orEmpty().flatMap(::descendants)
    private fun onEdt(action: () -> Unit) { SwingUtilities.invokeAndWait(action) }

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
    }

    private class FakeProvider(private val clock: Clock) : ThemeSummaryProvider {
        var summaryCalls = 0
        val memberCalls = mutableListOf<String>()
        var memberFailure: Exception? = null
        var emptyMembers = false
        var memberCount = 1
        override fun getDailySummary(): ThemeSummarySnapshot {
            ++summaryCalls
            return ThemeSummarySnapshot(
                listOf(ThemeSummary("a", "Alpha", 2.0, 1, 0, 0, clock.instant(), emptyList())),
                listOf(ThemeSummary("b", "Beta", -2.0, 0, 1, 0, clock.instant(), emptyList())), clock.instant())
        }
        override fun getThemeMembers(themeCode: String): List<ThemeConstituent> {
            memberCalls.add(themeCode)
            memberFailure?.let { throw it }
            return if (emptyMembers) emptyList() else (1..memberCount).map { index ->
                val name = "$themeCode member" + if (memberCount > 1) " $index" else ""
                ThemeConstituent(Ticker(themeCode, themeCode, name, MarketType.KOSPI, "KOR", "Korea"))
            }
        }
    }
}
