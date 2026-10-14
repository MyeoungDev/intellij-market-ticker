package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.model.theme.ThemeLoadState
import com.github.myeoungdev.marketticker.application.model.theme.ThemeRefreshThrottledException
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.ThemeSummaryService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeConstituent
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummary
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummarySnapshot
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Cursor
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.Timer
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

private data class ThemeCardComponents(
    val card: JPanel,
    val themeName: String,
    val titleButton: JButton,
    val lowerContent: JPanel,
    val memberSpacer: Component,
    val memberPanel: JPanel
)

/** 스크리너의 테마 모드가 표시될 때 국내 일간 테마 순위를 보여줍니다. */
class ThemeSummaryPanel internal constructor(
    private val themeSummaryService: ThemeSummaryService,
    ioDispatcher: CoroutineDispatcher,
    private val uiDispatcher: CoroutineDispatcher,
    private val onAddToWatchlist: (Ticker) -> Unit
) : JPanel(BorderLayout()), Disposable {

    constructor(onAddToWatchlist: (Ticker) -> Unit = {}) :
        this(service<ThemeSummaryService>(), Dispatchers.IO, Dispatchers.Main, onAddToWatchlist)

    private val localizationService = service<LocalizationService>()
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val titleLabel = JLabel()
    private val statusLabel = JLabel()
    private val refreshButton = JButton()
    private val sectionsPanel = JPanel().apply { name = OVERVIEW_SECTIONS_NAME }
    private val overviewScroll = JBScrollPane(sectionsPanel).apply {
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    }
    private val memberStatusLabel = JLabel()
    private val retryMembersButton = JButton()
    private val memberTableModel = object : DefaultTableModel(
        arrayOf(
            localizationService.text("종목", "Stock"),
            localizationService.text("등락률", "Change"),
            localizationService.text("거래량", "Volume")
        ),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val memberTable = JBTable(memberTableModel)
    private val memberTableScroll = JBScrollPane(memberTable).apply {
        name = MEMBER_TABLE_SCROLL_NAME
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    }
    private val memberTableView = JPanel(GridBagLayout()).apply { isOpaque = false }
    private val themeCards = mutableMapOf<String, ThemeCardComponents>()
    private var displayedMembers: List<ThemeConstituent> = emptyList()
    private var selectedTheme: ThemeSummary? = null
    private var activeExpandedThemeCode: String? = null
    private var latestState: ThemeLoadState<ThemeSummarySnapshot> = themeSummaryService.summaryState()
    private var requestSequence = 0L
    private var wasShowing = false
    private var disposed = false
    private val members = ThemeMemberInteraction(themeSummaryService, scope, uiDispatcher) {
        renderExpandedThemeState()
    }

    private val refreshTimer = Timer(REFRESH_INTERVAL_MS) {
        if (isShowing) loadSummary(force = false)
    }.apply { isRepeats = true }

    private val cooldownTimer = Timer(1_000) {
        updateRefreshButton()
    }.apply { isRepeats = true }

    private val layoutTimer = Timer(120) {
        if (!disposed && isShowing) renderContent(latestState)
    }.apply { isRepeats = false }

    init {
        border = JBUI.Borders.empty(8)
        add(buildHeader(), BorderLayout.NORTH)
        add(overviewScroll, BorderLayout.CENTER)
        add(statusLabel.apply { foreground = JBColor.GRAY }, BorderLayout.SOUTH)
        setupTexts()
        addHierarchyListener(HierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                syncVisibility()
            }
        })
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (!disposed && isShowing) layoutTimer.restart()
            }
        })
        refreshButton.addActionListener { loadSummary(force = true) }
        retryMembersButton.addActionListener { members.refresh(force = true) }
        memberTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val row = memberTable.rowAtPoint(event.point)
                if (row < 0) return
                memberTable.setRowSelectionInterval(row, row)
                if (event.clickCount == 2) addSelectedMember()
            }

            override fun mouseExited(event: MouseEvent) {
                memberTable.cursor = Cursor.getDefaultCursor()
            }
        })
        memberTable.inputMap.put(KeyStroke.getKeyStroke("ENTER"), ADD_MEMBER_ACTION)
        memberTable.actionMap.put(ADD_MEMBER_ACTION, object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = addSelectedMember()
        })
        renderContent(latestState)
        updateRefreshButton()
    }

    /** 상위 탭 선택이 바뀔 때 호출하며, 계층 가시성으로 바깥 탭의 표시 상태도 반영합니다. */
    fun syncVisibility() {
        if (disposed) return
        val visible = isShowing
        if (visible == wasShowing) return
        wasShowing = visible
        members.setVisible(visible)
        if (visible) {
            loadSummary(force = false)
            refreshTimer.start()
            updateRefreshButton()
        } else {
            ++requestSequence
            refreshTimer.stop()
            cooldownTimer.stop()
            layoutTimer.stop()
        }
    }

    override fun dispose() {
        disposed = true
        ++requestSequence
        members.dispose()
        wasShowing = false
        refreshTimer.stop()
        cooldownTimer.stop()
        layoutTimer.stop()
        scope.cancel()
    }

    private fun buildHeader(): JPanel = JPanel(BorderLayout(8, 0)).apply {
        isOpaque = false
        add(
            JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
                add(titleLabel)
            },
            BorderLayout.CENTER
        )
        add(refreshButton, BorderLayout.EAST)
        border = JBUI.Borders.emptyBottom(8)
    }

    private fun setupTexts() {
        titleLabel.text = localizationService.text("국내 · 일간 테마", "Korea · Daily Themes")
        refreshButton.text = localizationService.text("새로고침", "Refresh")
        statusLabel.text = localizationService.text("테마 데이터를 불러올 준비가 되었습니다.", "Theme data is ready to load.")
        retryMembersButton.text = localizationService.text("구성 종목 다시 시도", "Retry members")
        memberTable.rowHeight = JBUI.scale(28)
        memberTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        memberTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        memberTable.setShowGrid(false)
        memberTable.tableHeader.reorderingAllowed = false
        memberTable.columnModel.getColumn(1).cellRenderer = DefaultTableCellRenderer().apply {
            horizontalAlignment = SwingConstants.RIGHT
        }
        memberTable.columnModel.getColumn(2).cellRenderer = DefaultTableCellRenderer().apply {
            horizontalAlignment = SwingConstants.RIGHT
        }
        memberTable.columnModel.getColumn(0).preferredWidth = 170
        memberTable.columnModel.getColumn(1).preferredWidth = 80
        memberTable.columnModel.getColumn(2).preferredWidth = 100
        memberTable.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                val row = memberTable.rowAtPoint(event.point)
                memberTable.cursor = Cursor.getPredefinedCursor(
                    if (row >= 0) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR
                )
                memberTable.toolTipText = displayedMembers.getOrNull(row)?.let { member ->
                    listOfNotNull(member.ticker.name, member.ticker.symbol)
                        .joinToString(" · ")
                }
            }

        })
    }

    private fun loadSummary(force: Boolean) {
        if (disposed || !isShowing) return
        val requestId = ++requestSequence
        val current = themeSummaryService.summaryState()
        latestState = current.copy(isLoading = true, isStale = current.value != null)
        renderContent(latestState)
        updateRefreshButton()
        members.refresh(force)

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val failure = runCatching {
                if (force) themeSummaryService.refreshManually()
                else themeSummaryService.refreshIfStale(isVisible = isShowing)
            }.exceptionOrNull()
            withContext(uiDispatcher) {
                if (disposed || !isShowing || requestId != requestSequence) return@withContext
                latestState = themeSummaryService.summaryState()
                renderContent(latestState)
                updateStatus(failure)
                updateRefreshButton()
            }
        }
    }

    private fun updateStatus(failure: Throwable?) {
        val fetchedAt = latestState.fetchedAt
        val fetchedText = fetchedAt?.let(::formatTime)
        statusLabel.text = when {
            failure is ThemeRefreshThrottledException -> localizationService.text(
                "다음 수동 갱신까지 ${failure.retryAfter.seconds.coerceAtLeast(1)}초 남았습니다.",
                "Manual refresh available in ${failure.retryAfter.seconds.coerceAtLeast(1)}s."
            )
            failure != null && fetchedText != null -> localizationService.text(
                "갱신 실패 · 마지막 정상 조회 $fetchedText 표시 중",
                "Refresh failed · showing last successful fetch from $fetchedText"
            )
            failure != null -> localizationService.text(
                "테마 데이터를 불러오지 못했습니다. 다시 시도해 주세요.",
                "Could not load theme data. Try again."
            )
            latestState.error != null && fetchedText != null -> localizationService.text(
                "갱신 실패 · 마지막 정상 조회 $fetchedText 표시 중",
                "Refresh failed · showing last successful fetch from $fetchedText"
            )
            latestState.error != null -> localizationService.text(
                "테마 데이터를 불러오지 못했습니다. 새로고침을 눌러 다시 시도해 주세요.",
                "Theme data is unavailable. Use Refresh to retry."
            )
            fetchedText != null && latestState.isStale -> localizationService.text(
                "이전 데이터 표시 중 · 조회 $fetchedText",
                "Showing stale data · fetched $fetchedText"
            )
            fetchedText != null -> ""
            else -> localizationService.text("테마 데이터를 불러오는 중...", "Loading theme data...")
        }
    }

    private fun updateRefreshButton() {
        if (disposed || !isShowing) {
            refreshButton.isEnabled = false
            cooldownTimer.stop()
            return
        }

        val refreshState = themeSummaryService.manualRefreshState()
        refreshButton.isEnabled = refreshState.isAvailable && !latestState.isLoading
        val seconds = refreshState.retryAfter.toMillis().let { (it + 999) / 1_000 }.toInt()
        refreshButton.text = if (seconds > 0) {
            localizationService.text("새로고침 ${seconds}초", "Refresh ${seconds}s")
        } else {
            localizationService.text("새로고침", "Refresh")
        }
        refreshButton.toolTipText = localizationService.text(
            "갱신은 30초 간격으로 제한됩니다.",
            "Manual refresh is limited to once every 30 seconds."
        )
        if (seconds > 0) cooldownTimer.start() else cooldownTimer.stop()
    }

    private fun renderContent(state: ThemeLoadState<ThemeSummarySnapshot>) {
        state.value?.let { snapshot ->
            val updatedSelection = (snapshot.gainers + snapshot.losers)
                .firstOrNull { it.code == selectedTheme?.code }
            if (updatedSelection != null) selectedTheme = updatedSelection
        }
        renderMemberTable()
        renderOverview(state)
        updateStatus(null)
    }

    private fun renderMemberTable() {
        displayedMembers = members.state.value.orEmpty().take(MAX_THEME_MEMBERS)
        memberTableModel.rowCount = 0
        displayedMembers.forEach { member ->
            memberTableModel.addRow(arrayOf(
                member.ticker.name,
                member.changeRate?.let(::formatRate) ?: "—",
                member.volume?.let { NumberFormat.getIntegerInstance(localizationService.currentLocale()).format(it) } ?: "—"
            ))
        }
        updateMemberTableHeight()
    }

    private fun renderExpandedThemeState() {
        renderMemberTable()
        val expandedThemeCode = members.expandedThemeCode
        if (activeExpandedThemeCode != expandedThemeCode) {
            activeExpandedThemeCode?.let { code -> themeCards[code]?.let { setCardExpanded(it, false) } }
            activeExpandedThemeCode = expandedThemeCode?.takeIf(themeCards::containsKey)
            activeExpandedThemeCode?.let { code -> themeCards[code]?.let { setCardExpanded(it, true) } }
        }
        activeExpandedThemeCode?.let { code ->
            themeCards[code]?.let { parts ->
                renderMemberPanel(parts.memberPanel)
                updateCardSize(parts)
            }
        }
        sectionsPanel.revalidate()
        sectionsPanel.repaint()
    }

    private fun syncExpandedThemeCard() {
        activeExpandedThemeCode = members.expandedThemeCode?.takeIf(themeCards::containsKey)
        activeExpandedThemeCode?.let { code ->
            themeCards[code]?.let { parts ->
                setCardExpanded(parts, true)
                renderMemberPanel(parts.memberPanel)
                updateCardSize(parts)
            }
        }
    }

    private fun renderOverview(state: ThemeLoadState<ThemeSummarySnapshot>) {
        sectionsPanel.removeAll()
        themeCards.clear()
        activeExpandedThemeCode = null
        sectionsPanel.isOpaque = false
        sectionsPanel.layout = GridBagLayout()

        val snapshot = state.value
        if (snapshot == null) {
            val message = when {
                state.error != null -> localizationService.text(
                    "테마 데이터를 불러오지 못했습니다. 새로고침을 눌러 다시 시도해 주세요.",
                    "Theme data is unavailable. Use Refresh to retry."
                )
                state.isLoading -> localizationService.text("테마 데이터를 불러오는 중...", "Loading theme data...")
                else -> localizationService.text("테마 데이터가 없습니다.", "No theme data available.")
            }
            sectionsPanel.add(JLabel(message, SwingConstants.CENTER), GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                gridwidth = themeSummaryColumnCount(width)
                weightx = 1.0
                anchor = GridBagConstraints.NORTH
                fill = GridBagConstraints.HORIZONTAL
            })
        } else {
            val columns = themeSummaryColumnCount(width)
            addThemeSection(
                createThemeSection(localizationService.text("상승 테마 TOP 3", "Top 3 Rising Themes"), snapshot.gainers, true),
                gridX = 0,
                gridY = 0,
                columns = columns
            )
            addThemeSection(
                createThemeSection(localizationService.text("하락 테마 TOP 3", "Top 3 Falling Themes"), snapshot.losers, false),
                gridX = if (columns == 2) 1 else 0,
                gridY = if (columns == 2) 0 else 1,
                columns = columns
            )
        }
        syncExpandedThemeCard()
        sectionsPanel.revalidate()
        sectionsPanel.repaint()
    }

    private fun addThemeSection(section: JPanel, gridX: Int, gridY: Int, columns: Int) {
        sectionsPanel.add(section, GridBagConstraints().apply {
            gridx = gridX
            gridy = gridY
            weightx = if (columns == 2) 0.5 else 1.0
            weighty = 0.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTH
            insets = Insets(if (gridY == 0) 0 else 8, if (gridX == 0) 0 else 8, 0, 0)
        })
    }

    private fun createThemeSection(title: String, themes: List<ThemeSummary>, rising: Boolean): JPanel =
        JPanel().apply {
            isOpaque = false
            name = if (rising) RISING_SECTION_NAME else FALLING_SECTION_NAME
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                JBUI.Borders.empty(8)
            )
            add(JLabel(title).apply { font = font.deriveFont(Font.BOLD) })
            add(Box.createVerticalStrut(8))
            if (themes.isEmpty()) {
                add(JLabel(localizationService.text("해당 테마가 없습니다.", "No themes in this group.")).apply {
                    foreground = JBColor.GRAY
                })
            } else {
                themes.forEachIndexed { index, theme ->
                    add(createThemeCard(theme, rising))
                    if (index < themes.lastIndex) add(Box.createVerticalStrut(6))
                }
            }
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

    private fun createThemeCard(theme: ThemeSummary, rising: Boolean): JPanel =
        JPanel(BorderLayout(4, 6)).apply {
            isOpaque = true
            name = "$THEME_CARD_NAME_PREFIX${theme.code}"
            background = if (rising) RISING_BACKGROUND else FALLING_BACKGROUND
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(if (rising) RISING_COLOR else FALLING_COLOR),
                JBUI.Borders.empty(7, 8)
            )

            val titleButton = JButton("${theme.name}  ▾").apply {
                font = font.deriveFont(Font.BOLD)
                toolTipText = localizationService.text("구성 종목 펼치기 · ${theme.name}", "Expand constituents · ${theme.name}")
                horizontalAlignment = SwingConstants.LEFT
                isContentAreaFilled = false
                border = JBUI.Borders.empty(2)
                name = "theme-title-${theme.code}"
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                accessibleContext.accessibleName = localizationService.text(
                    "${theme.name} 종목 펼치기", "Expand ${theme.name} constituents"
                )
                addActionListener { toggleTheme(theme) }
            }
            val rate = JLabel(formatRate(theme.changeRate), SwingConstants.RIGHT).apply {
                font = font.deriveFont(Font.BOLD)
                foreground = if (rising) RISING_COLOR else FALLING_COLOR
            }
            add(JPanel(BorderLayout(6, 0)).apply {
                isOpaque = false
                add(titleButton, BorderLayout.CENTER)
                add(rate, BorderLayout.EAST)
            }, BorderLayout.NORTH)

            add(JLabel(localizationService.text(
                "상승 ${theme.risingCount} · 보합 ${theme.unchangedCount} · 하락 ${theme.fallingCount}",
                "Up ${theme.risingCount} · Flat ${theme.unchangedCount} · Down ${theme.fallingCount}"
            )).apply { foreground = JBColor.GRAY }, BorderLayout.CENTER)

            val leaderText = theme.leaders.joinToString(" · ") { leader ->
                "${leader.ticker.name} ${formatRate(leader.changeRate)}"
            }
            val leaders = JTextArea(3, 24).apply {
                text = leaderText.ifBlank { localizationService.text("대표 종목 정보 없음", "No representative stocks") }
                lineWrap = true
                wrapStyleWord = true
                isEditable = false
                isFocusable = false
                isOpaque = false
                font = font.deriveFont(font.size2D - 1f)
                foreground = JBColor.GRAY
                border = BorderFactory.createEmptyBorder()
                toolTipText = leaderText
            }
            val lowerContent = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(leaders)
            }
            val memberPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                name = "theme-members-${theme.code}"
            }
            add(lowerContent, BorderLayout.SOUTH)
            themeCards[theme.code] = ThemeCardComponents(
                card = this,
                themeName = theme.name,
                titleButton = titleButton,
                lowerContent = lowerContent,
                memberSpacer = Box.createVerticalStrut(6),
                memberPanel = memberPanel
            )

            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

    private fun setCardExpanded(parts: ThemeCardComponents, expanded: Boolean) {
        parts.titleButton.text = "${parts.themeName}  ${if (expanded) "▴" else "▾"}"
        parts.titleButton.toolTipText = localizationService.text(
            "${if (expanded) "구성 종목 접기" else "구성 종목 펼치기"} · ${parts.themeName}",
            "${if (expanded) "Collapse" else "Expand"} constituents · ${parts.themeName}"
        )
        parts.titleButton.accessibleContext.accessibleName = localizationService.text(
            "${parts.themeName} ${if (expanded) "종목 접기" else "종목 펼치기"}",
            "${if (expanded) "Collapse" else "Expand"} ${parts.themeName} constituents"
        )
        if (expanded) {
            if (parts.memberPanel.parent !== parts.lowerContent) {
                parts.lowerContent.add(parts.memberSpacer)
                parts.lowerContent.add(parts.memberPanel)
            }
        } else {
            parts.lowerContent.remove(parts.memberSpacer)
            parts.lowerContent.remove(parts.memberPanel)
        }
        updateCardSize(parts)
        parts.lowerContent.revalidate()
        parts.card.revalidate()
        parts.card.repaint()
    }

    private fun updateCardSize(parts: ThemeCardComponents) {
        parts.card.maximumSize = Dimension(Int.MAX_VALUE, parts.card.preferredSize.height)
        (parts.card.parent as? JPanel)?.let { section ->
            section.maximumSize = Dimension(Int.MAX_VALUE, section.preferredSize.height)
            section.revalidate()
        }
    }

    private fun renderMemberPanel(panel: JPanel) {
        val state = members.state
        memberStatusLabel.text = when {
            state.isLoading && state.value != null -> localizationService.text(
                "구성 종목 갱신 중 · 이전 데이터 표시", "Refreshing members · showing stale data")
            state.error != null && state.value != null -> localizationService.text(
                "구성 종목 갱신 실패 · 이전 데이터 표시", "Member refresh failed · showing stale data")
            state.error != null -> localizationService.text("종목 조회 실패", "Member load failed")
            state.isLoading -> ""
            state.isStale -> localizationService.text("이전 종목 데이터 표시 중", "Showing stale members")
            state.fetchedAt != null -> ""
            else -> ""
        }
        retryMembersButton.isVisible = state.error != null
        retryMembersButton.isEnabled = !state.isLoading

        panel.removeAll()
        panel.layout = BorderLayout(0, 5)
        panel.apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(4)
            val count = state.value?.size
            add(JLabel(localizationService.text(
                count?.let { "구성 종목 ${it}개 · 거래량 순" } ?: "구성 종목 · 거래량 순",
                count?.let { "$it constituents · volume order" } ?: "Constituents · volume order"
            )).apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.NORTH)

            when {
                state.isLoading && state.value == null -> add(
                    JLabel(localizationService.text("종목 불러오는 중...", "Loading members...")),
                    BorderLayout.CENTER
                )
                state.error != null && state.value == null -> add(
                    JLabel(localizationService.text("종목을 불러오지 못했습니다.", "Could not load members.")),
                    BorderLayout.CENTER
                )
                state.value?.isEmpty() == true -> add(
                    JLabel(localizationService.text("구성 종목이 없습니다.", "No members available.")),
                    BorderLayout.CENTER
                )
                else -> {
                    memberTableView.removeAll()
                    memberTableView.add(memberTableScroll, GridBagConstraints().apply {
                        gridx = 0
                        gridy = 0
                        weightx = 1.0
                        weighty = 0.0
                        fill = GridBagConstraints.HORIZONTAL
                        anchor = GridBagConstraints.NORTH
                    })
                    add(memberTableView, BorderLayout.CENTER)
                }
            }

            if (memberStatusLabel.text.isNotBlank() || retryMembersButton.isVisible) {
                add(JPanel(BorderLayout(8, 0)).apply {
                    isOpaque = false
                    add(memberStatusLabel, BorderLayout.CENTER)
                    if (retryMembersButton.isVisible) add(retryMembersButton, BorderLayout.EAST)
                }, BorderLayout.SOUTH)
            }
        }
        panel.revalidate()
        panel.repaint()
    }

    private fun updateMemberTableHeight() {
        val visibleRows = displayedMembers.size.coerceAtMost(MAX_VISIBLE_MEMBER_ROWS)
        val rowsHeight = visibleRows * memberTable.rowHeight
        val height = rowsHeight + memberTable.tableHeader.preferredSize.height + JBUI.scale(2)
        memberTable.preferredScrollableViewportSize = Dimension(320, rowsHeight)
        memberTableScroll.preferredSize = Dimension(320, height)
        memberTableScroll.minimumSize = Dimension(0, height)
        memberTableScroll.maximumSize = Dimension(Int.MAX_VALUE, height)
    }

    private fun toggleTheme(theme: ThemeSummary) {
        if (disposed || !isShowing) return
        selectedTheme = if (members.expandedThemeCode == theme.code) null else theme
        members.toggle(theme.code)
    }

    private fun addSelectedMember() {
        val theme = selectedTheme ?: return
        val selectedRow = memberTable.selectedRow
        if (selectedRow < 0) return
        val member = displayedMembers.getOrNull(memberTable.convertRowIndexToModel(selectedRow)) ?: return
        if (!disposed && isShowing && members.expandedThemeCode == theme.code) {
            onAddToWatchlist(member.ticker)
        }
    }

    private fun formatRate(rate: Double): String {
        val sign = if (rate > 0) "+" else ""
        return "$sign${localizationService.formatPercentFixed(rate, 2)}"
    }

    private fun formatTime(time: Instant): String =
        TIME_FORMATTER.withLocale(localizationService.currentLocale())
            .withZone(ZoneId.systemDefault()).format(time)

    companion object {
        private const val ADD_MEMBER_ACTION = "add-theme-member-to-watchlist"
        private const val MAX_THEME_MEMBERS = 20
        private const val MAX_VISIBLE_MEMBER_ROWS = 5
        private const val MEMBER_TABLE_SCROLL_NAME = "theme-member-table-scroll"
        private const val OVERVIEW_SECTIONS_NAME = "theme-summary-sections"
        private const val RISING_SECTION_NAME = "theme-summary-rising"
        private const val FALLING_SECTION_NAME = "theme-summary-falling"
        private const val THEME_CARD_NAME_PREFIX = "theme-summary-card-"
        private const val REFRESH_INTERVAL_MS = 5 * 60 * 1_000
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        private val RISING_COLOR = JBColor(Color(191, 49, 57), Color(255, 107, 114))
        private val FALLING_COLOR = JBColor(Color(37, 113, 196), Color(105, 171, 243))
        private val RISING_BACKGROUND = JBColor(Color(255, 246, 246), Color(68, 43, 46))
        private val FALLING_BACKGROUND = JBColor(Color(244, 249, 255), Color(40, 50, 66))
    }
}
