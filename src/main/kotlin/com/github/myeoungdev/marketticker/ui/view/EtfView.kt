package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.repository.EtfWatchlistRepository
import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.EtfService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.domain.model.etf.EtfSort
import com.github.myeoungdev.marketticker.domain.model.etf.EtfExposureFilter
import com.github.myeoungdev.marketticker.domain.model.etf.EtfPerformancePeriod
import com.github.myeoungdev.marketticker.domain.model.etf.EtfSummary
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicLong
import java.util.Locale
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.table.DefaultTableModel

class EtfView : JPanel(BorderLayout()), Disposable {
    private val localization = service<LocalizationService>()
    private val service = service<EtfService>()
    private val watchlist = service<EtfWatchlistRepository>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rows = mutableListOf<EtfSummary>()
    private val renderedEtfs = mutableMapOf<DefaultTableModel, List<EtfSummary>>()
    private val compare = linkedMapOf<String, EtfSummary>()
    private val status = JLabel()
    private val search = JTextField(14)
    private val sort = JComboBox(DefaultComboBoxModel(EtfSort.values()))
    private val category = JComboBox<String>()
    private val exposureFilter = JComboBox(EtfExposureFilter.values())
    private val discoveryModel = model(arrayOf(localization.text("ETF", "ETF"), localization.text("현재가", "Price"), localization.text("등락률", "Change %"), localization.text("1개월", "1M")))
    private val favoritesModel = model(arrayOf(localization.text("ETF", "ETF"), localization.text("그룹", "Group"), localization.text("현재가", "Price"), localization.text("등락률", "Change %")))
    private val comparisonModel = model(arrayOf(localization.text("ETF", "ETF"), localization.text("현재가", "Price"), localization.text("수익률", "Return")))
    private var currentPage = 0
    private var canLoadNext = false
    private var pageLoading = false
    private val pageSequence = AtomicLong()
    private val pageLabel = JLabel()
    private val discoveryTable = JBTable(discoveryModel)
    private val discoveryScrollPane = JBScrollPane(discoveryTable)
    private val previousPageButton = JButton(localization.text("‹", "‹"))
    private val nextPageButton = JButton(localization.text("›", "›"))
    private val favoritesTable = JBTable(favoritesModel)
    private val comparisonTable = JBTable(comparisonModel)
    private val settings = service<AppSettingsService>()
    private val comparisonPeriodLabel = JLabel()
    private val detailPeriodLabel = JLabel()
    private val comparisonDetails = mutableMapOf<String, EtfSummary>()
    private val detailSequence = AtomicLong()
    private var selectedDetail: EtfSummary? = null
    private val detailTitle = JLabel(localization.text("ETF를 더블클릭해 상세 정보를 확인하세요.", "Double-click an ETF to view details."))
    private val detailPerformance = JLabel()
    private val detailTabs = JTabbedPane()
    private val detailOverview = JPanel(GridLayout(0, 1, 0, 4))
    private val detailComponentsModel = object : DefaultTableModel(arrayOf(
        localization.text("편입 종목", "Holding"), localization.text("코드", "Code"),
        localization.text("비중", "Weight"), localization.text("평가액", "Value"),
        localization.text("기준일", "As of")
    ), 0) { override fun isCellEditable(row: Int, column: Int) = false }
    private val detailChart = ChartView()
    private val detailPanel = JPanel(BorderLayout())
    private lateinit var splitPane: javax.swing.JSplitPane

    init {
        border = JBUI.Borders.empty(8)
        val tabs = JTabbedPane()
        tabs.addTab(localization.text("발견", "Discover"), discoveryPanel())
        tabs.addTab(localization.text("관심 ETF", "Watchlist"), favoritesPanel())
        tabs.addTab(localization.text("비교", "Compare"), comparisonPanel())
        buildDetailPanel()
        splitPane = javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, tabs, detailPanel).apply {
            resizeWeight = 0.68
            dividerSize = 6
            border = null
            minimumSize = Dimension(0, 0)
            bottomComponent = null
        }
        add(splitPane, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        listOf(discoveryTable, favoritesTable, comparisonTable).forEach(::compactTable)
        installDiscoveryActions()
        installFavoritesActions()
        installComparisonActions()
        sort.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): java.awt.Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val item = value as? EtfSort
                label.text = when (item) {
                    EtfSort.RETURN_RATE -> localization.text("${settings.getEtfPerformancePeriod().labelKo} 수익률순", "${settings.getEtfPerformancePeriod().labelEn} return")
                    null -> ""
                    else -> localization.text(item.labelKo, item.labelEn)
                }
                return label
            }
        }
        exposureFilter.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): java.awt.Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val item = value as? EtfExposureFilter
                label.text = item?.let { localization.text(it.labelKo, it.labelEn) } ?: ""
                return label
            }
        }
        category.renderer = filterRenderer("전체 유형", "All types")
        setCompactWidth(sort, 124)
        setCompactWidth(category, 112)
        setCompactWidth(exposureFilter, 100)
        sort.addActionListener { renderDiscovery() }
        category.addActionListener { renderDiscovery() }
        exposureFilter.addActionListener { renderDiscovery() }
        previousPageButton.toolTipText = localization.text("이전 페이지", "Previous page")
        nextPageButton.toolTipText = localization.text("다음 페이지", "Next page")
        previousPageButton.addActionListener { if (currentPage > 0 && !pageLoading) requestPage(currentPage - 1) }
        nextPageButton.addActionListener { if (canLoadNext && !pageLoading) requestPage(currentPage + 1) }
        updatePageButtons()
        search.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = renderDiscovery()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = renderDiscovery()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = renderDiscovery()
        })
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(SettingsUpdateListener.TOPIC, object : SettingsUpdateListener {
            override fun onSettingsUpdated() {
                ApplicationManager.getApplication().invokeLater {
                    comparisonPeriodLabel.text = selectedPeriodLabel()
                    detailPeriodLabel.text = selectedPeriodLabel()
                    renderDiscovery()
                    renderComparison()
                    renderDetailPerformance()
                }
            }
        })
        comparisonPeriodLabel.text = selectedPeriodLabel()
        detailPeriodLabel.text = selectedPeriodLabel()
        tabs.addChangeListener { if (tabs.selectedIndex == 2) loadComparisonDetails() }
        load()
    }

    override fun dispose() {
        scope.cancel()
        detailChart.dispose()
    }

    private fun discoveryPanel(): JPanel = JPanel(BorderLayout()).apply {
        val toolbar = JPanel(GridLayout(0, 1, 0, 3))
        toolbar.add(JPanel(BorderLayout(4, 0)).apply {
            add(search, BorderLayout.CENTER)
            add(JButton(localization.text("새로고침", "Refresh")).apply { addActionListener { load(true) } }, BorderLayout.EAST)
        })
        toolbar.add(JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0)).apply {
            add(sort)
            add(category)
            add(exposureFilter)
        })
        add(toolbar, BorderLayout.NORTH)
        add(discoveryScrollPane, BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            add(previousPageButton, BorderLayout.WEST)
            add(pageLabel, BorderLayout.CENTER)
            pageLabel.horizontalAlignment = JLabel.CENTER
            add(nextPageButton, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
    }

    private fun favoritesPanel(): JPanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(favoritesTable), BorderLayout.CENTER)
    }

    private fun comparisonPanel(): JPanel = JPanel(BorderLayout()).apply {
        add(JPanel(BorderLayout()).apply {
            add(JLabel(localization.text("수익률", "Return")), BorderLayout.WEST)
            add(comparisonPeriodLabel, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(JBScrollPane(comparisonTable), BorderLayout.CENTER)
    }

    private fun buildDetailPanel() {
        val performanceRow = JPanel(BorderLayout(8, 0)).apply {
            border = JBUI.Borders.empty(6, 8)
            add(JLabel(localization.text("수익률", "Return")), BorderLayout.WEST)
            add(detailPeriodLabel, BorderLayout.CENTER)
            add(detailPerformance, BorderLayout.EAST)
        }
        val overview = JPanel(BorderLayout())
        overview.add(performanceRow, BorderLayout.NORTH)
        overview.add(JBScrollPane(detailOverview), BorderLayout.CENTER)
        detailTabs.addTab(localization.text("개요", "Overview"), overview)
        detailTabs.addTab(localization.text("차트", "Chart"), detailChart)
        detailTabs.addTab(localization.text("편입 종목", "Holdings"), JBScrollPane(JBTable(detailComponentsModel)))
        detailTabs.isVisible = false
        detailPanel.add(detailTitle, BorderLayout.NORTH)
        detailPanel.add(JLabel(localization.text("목록에서 ETF를 더블클릭하면 상세 정보가 여기에 표시됩니다.", "Double-click an ETF to show its details here.")), BorderLayout.CENTER)
    }

    private fun selectedPeriodLabel(): String {
        val period = settings.getEtfPerformancePeriod()
        return localization.text(period.labelKo, period.labelEn)
    }

    private fun load(force: Boolean = false) = requestPage(0, force)

    private fun requestPage(page: Int, force: Boolean = false) {
        val requestId = pageSequence.incrementAndGet()
        pageLoading = true
        updatePageButtons()
        status.text = localization.text("국내 ETF를 불러오는 중...", "Loading domestic ETFs...")
        scope.launch {
            runCatching { service.loadPage(page, force) }.onSuccess { values ->
                ApplicationManager.getApplication().invokeLater {
                    if (requestId != pageSequence.get()) return@invokeLater
                    rows.clear()
                    rows.addAll(values)
                    currentPage = page
                    canLoadNext = values.size >= 100
                    pageLoading = false
                    pageLabel.text = localization.text("${page + 1} 페이지", "Page ${page + 1}")
                    val selectedCategory = category.selectedItem?.toString()
                    category.removeAllItems()
                    category.addItem(localization.text("전체 유형", "All types"))
                    rows.mapNotNull { it.category }.distinct().sorted().forEach(category::addItem)
                    if (selectedCategory != null) category.selectedItem = selectedCategory
                    renderDiscovery(); renderFavorites()
                    discoveryScrollPane.viewport.viewPosition = java.awt.Point(0, 0)
                    updatePageButtons()
                }
            }.onFailure { error ->
                ApplicationManager.getApplication().invokeLater {
                    if (requestId != pageSequence.get()) return@invokeLater
                    pageLoading = false
                    updatePageButtons()
                    status.text = localization.text("ETF 조회 실패: ${error.message ?: "네트워크 오류"}", "ETF request failed: ${error.message ?: "network error"}")
                }
            }
        }
    }

    private fun renderDiscovery() {
        val query = search.text.trim().lowercase()
        val allTypes = localization.text("전체 유형", "All types")
        val selectedCategory = category.selectedItem?.toString()
        val selectedExposure = exposureFilter.selectedItem as? EtfExposureFilter ?: EtfExposureFilter.ALL
        val sorted = rows.filter { (query.isBlank() || it.name.lowercase().contains(query) || it.code.lowercase().contains(query)) &&
            (selectedCategory.isNullOrBlank() || selectedCategory == allTypes || it.category == selectedCategory) &&
            selectedExposure.matches(it) }
            .let { list -> when (sort.selectedItem as? EtfSort ?: EtfSort.TRADING_VALUE) {
                EtfSort.TRADING_VALUE -> list.sortedByDescending { it.tradingValue ?: Double.NEGATIVE_INFINITY }
                EtfSort.RETURN_RATE -> list.sortedByDescending { settings.getEtfPerformancePeriod().rate(it) ?: Double.NEGATIVE_INFINITY }
                EtfSort.CHANGE_RATE -> list.sortedByDescending { it.changeRate ?: Double.NEGATIVE_INFINITY }
                EtfSort.ASSETS -> list.sortedByDescending { it.netAssets ?: Double.NEGATIVE_INFINITY }
                EtfSort.NAME -> list.sortedBy { it.name }
            } }
        fill(discoveryModel, sorted)
        status.text = localization.text(
            "${currentPage + 1}페이지 · ${rows.size}개 중 ${sorted.size}개 표시",
            "Page ${currentPage + 1} · showing ${sorted.size} of ${rows.size} ETFs"
        )
    }

    private fun updatePageButtons() {
        previousPageButton.isEnabled = !pageLoading && currentPage > 0
        nextPageButton.isEnabled = !pageLoading && canLoadNext
    }

    private fun renderFavorites() {
        val entries = watchlist.entries()
        fill(favoritesModel, entries.mapNotNull { entry ->
            (rows.firstOrNull { it.code == entry.code } ?: EtfSummary(entry.code, entry.name)).copy(category = entry.group)
        })
    }

    private fun addCompare(etf: EtfSummary) {
        if (compare.containsKey(etf.code)) return
        if (compare.size >= 4) {
            JOptionPane.showMessageDialog(this, localization.text("ETF는 최대 4개까지 비교할 수 있습니다.", "Compare up to 4 ETFs."))
            return
        }
        compare[etf.code] = etf
        renderComparison()
    }

    private fun showDetail(etf: EtfSummary) {
        val requestId = detailSequence.incrementAndGet()
        selectedDetail = etf
        detailTitle.text = localization.text("${etf.name} 상세 정보 로딩 중...", "Loading ${etf.name} details...")
        detailPerformance.text = ""
        splitPane.bottomComponent = detailPanel
        ApplicationManager.getApplication().invokeLater { splitPane.setDividerLocation(0.68) }
        status.text = localization.text("ETF 상세 정보를 불러오는 중...", "Loading ETF details...")

        scope.launch {
            val result = runCatching { service.loadDetail(etf.code) }
            ApplicationManager.getApplication().invokeLater {
                if (requestId != detailSequence.get()) return@invokeLater
                val detail = result.getOrNull()
                if (detail == null) {
                    detailTitle.text = localization.text("상세 정보를 불러오지 못했습니다.", "Could not load ETF details.")
                    status.text = localization.text("상세 정보 조회에 실패했습니다.", "Failed to load ETF details.")
                    return@invokeLater
                }
                selectedDetail = detail.summary
                detailTitle.text = "${detail.summary.name} (${detail.summary.code})"
                renderDetailOverview()
                detailComponentsModel.rowCount = 0
                detail.components.forEach { item ->
                    detailComponentsModel.addRow(arrayOf(item.name, item.code ?: "—", item.weight.percent(), item.evaluationAmount.money(), item.referenceDate ?: "—"))
                }
                if (detail.components.isEmpty()) detailComponentsModel.addRow(arrayOf(localization.text("구성 종목 정보 없음", "No holdings data"), "—", "—", "—", "—"))
                detailChart.updateSelection(Ticker(etf.code, etf.code, etf.name, MarketType.KOREA, "KOR", "대한민국"))
                detailTabs.isVisible = true
                detailPanel.removeAll()
                detailPanel.add(detailTitle, BorderLayout.NORTH)
                detailPanel.add(detailTabs, BorderLayout.CENTER)
                detailPanel.revalidate()
                detailPanel.repaint()
                status.text = ""
            }
        }
    }

    private fun renderDetailOverview() {
        detailOverview.removeAll()
        val summary = selectedDetail ?: return
        listOf(
            "${localization.text("운용사", "Manager")}: ${summary.manager ?: "—"}",
            "${localization.text("추종지수", "Index")}: ${summary.indexName ?: "—"}",
            "${localization.text("유형", "Category")}: ${summary.category ?: "—"}",
            "${localization.text("총보수", "Total fee")}: ${summary.totalFee.percent()}",
            "${localization.text("순자산", "Net assets")}: ${summary.netAssets.money()}",
            "${localization.text("분배율", "Distribution yield")}: ${summary.distributionYield.percent()}",
            "${localization.text("환헤지", "Currency hedge")}: ${summary.hedgeType ?: "—"}",
            "${localization.text("기준일", "As of")}: ${summary.referenceDate ?: "—"}"
        ).forEach { detailOverview.add(JLabel(it)) }
        renderDetailPerformance()
        detailOverview.revalidate()
        detailOverview.repaint()
    }

    private fun renderDetailPerformance() {
        val summary = selectedDetail ?: return
        val period = settings.getEtfPerformancePeriod()
        val value = period.rate(summary)
        detailPerformance.text = value.percent()
    }

    private fun loadComparisonDetails() {
        val missing = compare.keys.filterNot(comparisonDetails::containsKey)
        if (missing.isEmpty()) return
        scope.launch {
            val loaded = missing.mapNotNull { code ->
                runCatching { service.loadDetail(code)?.summary?.let { code to it } }.getOrNull()
            }
            ApplicationManager.getApplication().invokeLater {
                loaded.forEach { (code, summary) -> comparisonDetails[code] = summary }
                renderComparison()
            }
        }
    }

    private fun renderComparison() {
        val period = settings.getEtfPerformancePeriod()
        val values = compare.values.toList()
        renderedEtfs[comparisonModel] = values.map { comparisonDetails[it.code] ?: it }
        comparisonModel.rowCount = 0
        values.forEach { etf ->
            val details = comparisonDetails[etf.code] ?: etf
            comparisonModel.addRow(arrayOf(etf.name, etf.price.money(), period.rate(details).percent()))
        }
    }

    private fun compactTable(table: JBTable) {
        table.rowHeight = 25
        table.autoResizeMode = JBTable.AUTO_RESIZE_ALL_COLUMNS
        val widths = when (table.columnModel.columnCount) {
            3 -> listOf(145, 80, 72)
            4 -> listOf(145, 78, 58, 58)
            else -> listOf(135, 68, 78, 58)
        }
        widths.forEachIndexed { index, width ->
            table.columnModel.getColumn(index).preferredWidth = width
            table.columnModel.getColumn(index).minWidth = if (index == 0) 90 else 48
        }
    }

    private fun filterRenderer(allKo: String, allEn: String) = object : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): java.awt.Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            label.text = value?.toString() ?: localization.text(allKo, allEn)
            return label
        }
    }

    private fun setCompactWidth(combo: JComboBox<*>, width: Int) {
        val size = Dimension(width, combo.preferredSize.height)
        combo.preferredSize = size
        combo.minimumSize = size
        combo.maximumSize = size
    }

    private fun EtfExposureFilter.matches(etf: EtfSummary): Boolean {
        val name = etf.name.lowercase()
        val metadata = etf.leverageType.orEmpty().lowercase()
        val isInverse = "인버스" in name || "inverse" in metadata
        val isLeveraged = !isInverse && ("레버리지" in name || "leverag" in metadata || "2x" in name || "곱버스" in name)
        return when (this) {
            EtfExposureFilter.ALL -> true
            EtfExposureFilter.REGULAR -> !isInverse && !isLeveraged
            EtfExposureFilter.LEVERAGED -> isLeveraged
            EtfExposureFilter.INVERSE -> isInverse
        }
    }

    private fun installDiscoveryActions() = installRowMenu(discoveryTable) { etf, menu ->
        menu.addItem(localization.text("관심 목록에 추가", "Add to Watchlist")) { watchlist.add(etf); renderFavorites() }
        menu.addItem(localization.text("비교에 추가", "Add to Compare")) { addCompare(etf) }
        menu.addItem(localization.text("상세 보기", "Details")) { showDetail(etf) }
    }

    private fun installFavoritesActions() = installRowMenu(favoritesTable) { etf, menu ->
        menu.addItem(localization.text("그룹 변경", "Change Group")) {
            val group = JOptionPane.showInputDialog(this, localization.text("그룹 이름", "Group name"), "기본") ?: return@addItem
            watchlist.move(etf.code, group)
            renderFavorites()
        }
        menu.addItem(localization.text("비교에 추가", "Add to Compare")) { addCompare(etf) }
        menu.addItem(localization.text("상세 보기", "Details")) { showDetail(etf) }
        menu.addItem(localization.text("관심 목록에서 제거", "Remove from Watchlist")) { watchlist.remove(etf.code); renderFavorites() }
    }

    private fun installComparisonActions() = installRowMenu(comparisonTable) { etf, menu ->
        menu.addItem(localization.text("비교에서 제거", "Remove from Compare")) { compare.remove(etf.code); comparisonDetails.remove(etf.code); renderComparison() }
        menu.addItem(localization.text("상세 보기", "Details")) { showDetail(etf) }
    }

    private fun installRowMenu(table: JBTable, build: (EtfSummary, javax.swing.JPopupMenu) -> Unit) {
        table.addMouseListener(object : MouseAdapter() {
            private fun showPopup(event: MouseEvent) {
                if (!event.isPopupTrigger) return
                val row = table.rowAtPoint(event.point)
                if (row !in 0 until table.rowCount) return
                table.setRowSelectionInterval(row, row)
                val etf = selected(table) ?: return
                val menu = javax.swing.JPopupMenu()
                build(etf, menu)
                menu.show(table, event.x, event.y)
            }

            override fun mousePressed(event: MouseEvent) = showPopup(event)
            override fun mouseReleased(event: MouseEvent) = showPopup(event)
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) selected(table)?.let(::showDetail)
            }
        })
    }

    private fun javax.swing.JPopupMenu.addItem(label: String, action: () -> Unit) {
        add(javax.swing.JMenuItem(label).apply { addActionListener { action() } })
    }

    private fun selected(table: JBTable): EtfSummary? {
        val row = table.selectedRow.takeIf { it >= 0 }?.let(table::convertRowIndexToModel) ?: return null
        return renderedEtfs[table.model as DefaultTableModel]?.getOrNull(row)
    }

    private fun fill(model: DefaultTableModel, values: List<EtfSummary>) {
        renderedEtfs[model] = values
        model.rowCount = 0
        values.forEach { e ->
            val cells = when (model.columnCount) {
                4 -> when (model) {
                    discoveryModel -> arrayOf(e.name, e.price.money(), e.changeRate.percent(), e.returnRate1m.percent())
                    comparisonModel -> arrayOf(e.name, e.price.money(), e.returnRate1m.percent(), e.returnRate3m.percent())
                    else -> arrayOf(e.name, e.category.orEmpty(), e.price.money(), e.changeRate.percent())
                }
                else -> emptyArray()
            }
            model.addRow(cells)
        }
    }

    private fun model(columns: Array<String>) = object : DefaultTableModel(columns, 0) { override fun isCellEditable(row: Int, column: Int) = false }

    private fun Double?.money(): String = this?.let { String.format(Locale.getDefault(), "%,.0f", it) } ?: "—"
    private fun Double?.percent(): String = this?.let { String.format(Locale.getDefault(), "%.2f%%", it) } ?: "—"
}
