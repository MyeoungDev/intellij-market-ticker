package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.repository.EtfWatchlistRepository
import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.EtfService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.domain.model.etf.EtfSort
import com.github.myeoungdev.marketticker.domain.model.etf.EtfExposureFilter
import com.github.myeoungdev.marketticker.domain.model.etf.EtfPerformancePeriod
import com.github.myeoungdev.marketticker.domain.model.etf.EtfDiscoveryReturnPeriod
import com.github.myeoungdev.marketticker.domain.model.etf.EtfSummary
import com.github.myeoungdev.marketticker.domain.model.etf.EtfDetail
import com.github.myeoungdev.marketticker.domain.model.etf.EtfComponent
import com.github.myeoungdev.marketticker.domain.model.etf.EtfDistribution
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicLong
import java.util.Locale
import java.time.LocalDate
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JCheckBox
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
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
    private val discoveryModel = model(arrayOf(localization.text("ETF", "ETF"), localization.text("현재가", "Price"), localization.text("등락률", "Change %"), localization.text("3개월 수익률", "3 months return")))
    private val favoritesModel = model(arrayOf(localization.text("ETF", "ETF"), localization.text("현재가", "Price"), localization.text("등락률", "Change %")))
    private val comparisonModel = model(arrayOf(localization.text("구성 종목", "Holding")))
    private val comparisonMetricsModel = model(arrayOf(localization.text("지표", "Metric")))
    private val allocationModel = model(arrayOf(localization.text("배분 항목", "Allocation")))
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
    private val comparisonMetricsTable = JBTable(comparisonMetricsModel)
    private val allocationTable = JBTable(allocationModel)
    private val allocationReferenceLabel = JLabel()
    private val comparisonContentTabs = JTabbedPane()
    private val returnsChart = EtfComparisonBarChart(centered = true, valueSuffix = "%", emptyMessage = localization.text("수익률 데이터가 없습니다.", "No return data."))
    private val distributionsChart = EtfComparisonBarChart(centered = false, valueSuffix = "원", emptyMessage = localization.text("분배 이력이 없습니다.", "No distribution history."))
    private val flowsChart = EtfComparisonBarChart(centered = true, valueSuffix = "", emptyMessage = localization.text("자금 흐름 데이터가 없습니다.", "No fund flow data."))
    private val comparisonMetricsScroll = JBScrollPane(comparisonMetricsTable)
    private val settings = service<AppSettingsService>()
    private val detailPeriodLabel = JLabel()
    private val comparisonDetails = mutableMapOf<String, EtfDetail>()
    private val distributionsLoaded = mutableSetOf<String>()
    private val distributionsLoading = mutableSetOf<String>()
    private val comparisonSummary = JLabel()
    private val commonOnly = JCheckBox(localization.text("공통 종목만", "Shared only"))
    private val removeCompare = JComboBox<String>()
    private val removeCompareButton = JButton(localization.text("제거", "Remove"))
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
        commonOnly.addActionListener { renderComparison() }
        removeCompareButton.addActionListener {
            val code = (removeCompare.selectedItem as? String)?.substringBefore(" · ") ?: return@addActionListener
            compare.remove(code)?.let {
                comparisonDetails.remove(code)
                distributionsLoaded.remove(code)
                distributionsLoading.remove(code)
                renderComparison()
            }
        }
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
                    detailPeriodLabel.text = selectedPeriodLabel()
                    updateDiscoveryReturnColumn()
                    renderDiscovery()
                    renderComparison()
                    renderDetailPerformance()
                }
            }
        })
        detailPeriodLabel.text = selectedPeriodLabel()
        updateDiscoveryReturnColumn()
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
        add(JPanel(BorderLayout(6, 2)).apply {
            border = JBUI.Borders.emptyBottom(6)
            add(JPanel(BorderLayout()).apply {
                add(comparisonSummary, BorderLayout.CENTER)
                add(JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0)).apply {
                    add(commonOnly)
                }, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0)).apply {
                add(JLabel(localization.text("비교 ETF 제거", "Remove ETF")))
                add(removeCompare)
                add(removeCompareButton)
            }, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        comparisonTable.autoResizeMode = JBTable.AUTO_RESIZE_OFF
        comparisonMetricsTable.autoResizeMode = JBTable.AUTO_RESIZE_OFF
        comparisonMetricsTable.rowHeight = 24
        comparisonMetricsTable.tableHeader.preferredSize = Dimension(0, 28)
        comparisonMetricsTable.toolTipText = localization.text(
            "분배 기준일은 배당락일이며, 증가율은 주당 분배금의 단순 비교로 액면변경을 보정하지 않습니다.",
            "Distribution date is the ex-dividend date. Growth compares per-unit amounts and does not adjust for unit splits."
        )
        comparisonContentTabs.addTab(localization.text("요약", "Summary"), comparisonMetricsScroll)
        comparisonContentTabs.addTab(localization.text("구성 종목", "Holdings"), JBScrollPane(comparisonTable))
        comparisonContentTabs.addTab(localization.text("수익률", "Returns"), returnsChart)
        comparisonContentTabs.addTab(localization.text("분배금", "Distributions"), distributionsChart)
        allocationTable.autoResizeMode = JBTable.AUTO_RESIZE_OFF
        comparisonContentTabs.addTab(localization.text("자금·배분", "Flows & allocation"), JPanel(GridLayout(2, 1, 0, 6)).apply {
            add(flowsChart)
            add(JPanel(BorderLayout()).apply {
                add(allocationReferenceLabel, BorderLayout.NORTH)
                add(JBScrollPane(allocationTable), BorderLayout.CENTER)
            })
        })
        add(comparisonContentTabs, BorderLayout.CENTER)
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
            rows.firstOrNull { it.code == entry.code } ?: EtfSummary(entry.code, entry.name)
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
        val requestedCodes = missing.toSet()
        scope.launch {
            val loaded = missing.mapNotNull { code ->
                runCatching { service.loadDetail(code)?.let { code to it } }.getOrNull()
            }
            ApplicationManager.getApplication().invokeLater {
                loaded.filter { (code, _) -> code in requestedCodes && code in compare }.forEach { (code, detail) -> comparisonDetails[code] = detail }
                renderComparison()
                loadComparisonDistributions()
            }
        }
    }

    private fun loadComparisonDistributions() {
        val requestedCodes = compare.keys.filter { it in comparisonDetails && it !in distributionsLoaded && it !in distributionsLoading }
        if (requestedCodes.isEmpty()) return
        distributionsLoading.addAll(requestedCodes)
        scope.launch {
            val loaded = requestedCodes.mapNotNull { code ->
                runCatching { code to service.loadDistributions(code) }.getOrNull()
            }
            ApplicationManager.getApplication().invokeLater {
                distributionsLoading.removeAll(requestedCodes.toSet())
                loaded.filter { (code, _) -> code in compare }.forEach { (code, distributions) ->
                    comparisonDetails[code]?.let {
                        comparisonDetails[code] = it.copy(
                            distributions = distributions.items,
                            distributionAmountTtm = distributions.amountTtm,
                            distributionYieldTtm = distributions.yieldTtm,
                            distributionCountThisYear = distributions.countThisYear,
                            distributionMonthsThisYear = distributions.monthsThisYear,
                            distributionCountLastYear = distributions.countLastYear,
                            distributionMonthsLastYear = distributions.monthsLastYear
                        )
                    }
                    distributionsLoaded.add(code)
                }
                renderComparison()
            }
        }
    }

    private fun renderComparison() {
        val values = compare.values.toList()
        val details = values.map { comparisonDetails[it.code] }
        renderComparisonMetrics(values, details)
        returnsChart.updateData(
            values.map { it.name },
            listOf(
                localization.text("1개월", "1 month") to values.map { it.returnRate1m },
                localization.text("3개월", "3 months") to values.map { it.returnRate3m },
                localization.text("6개월", "6 months") to values.map { it.returnRate6m },
                localization.text("1년", "1 year") to values.map { it.returnRate1y }
            )
        )
        distributionsChart.updateData(values.map { it.name }, distributionChartData(details))
        flowsChart.updateData(values.map { it.name }, listOf(
            localization.text("자금 흐름 1개월", "Fund flow · 1 month") to values.map { etf -> comparisonDetails[etf.code]?.summary?.flowMonth ?: etf.flowMonth },
            localization.text("자금 흐름 3개월", "Fund flow · 3 months") to values.map { etf -> comparisonDetails[etf.code]?.summary?.flow3m ?: etf.flow3m },
            localization.text("자금 흐름 6개월", "Fund flow · 6 months") to values.map { etf -> comparisonDetails[etf.code]?.summary?.flow6m ?: etf.flow6m }
        ))
        renderAllocations(values, details)
        val headers = arrayOf(localization.text("구성 종목", "Holding")) + values.map { it.name.take(14) }.toTypedArray()
        comparisonModel.setColumnIdentifiers(headers)
        comparisonModel.rowCount = 0
        val topHoldings = details.map { detail -> detail?.components.orEmpty()
            .filter { it.weight != null }
            .sortedByDescending { it.weight }
            .take(10)
        }
        val keysByEtf = topHoldings.map { holdings -> holdings.map(::holdingKey).toSet() }
        val allKeys = topHoldings.flatten().map(::holdingKey).distinct()
        val sharedKeys = allKeys.filter { key -> keysByEtf.count { key in it } > 1 }.toSet()
        val visibleKeys = if (commonOnly.isSelected) sharedKeys else allKeys.toSet()
        val holdingsByKey = topHoldings.flatten().associateBy(::holdingKey)
        val orderedKeys = visibleKeys.sortedByDescending { key ->
            topHoldings.indices.maxOfOrNull { index -> topHoldings[index].firstOrNull { holdingKey(it) == key }?.weight ?: Double.NEGATIVE_INFINITY } ?: Double.NEGATIVE_INFINITY
        }
        orderedKeys.forEach { key ->
            val component = holdingsByKey.getValue(key)
            val row = arrayOfNulls<Any>(values.size + 1)
            row[0] = component.name
            values.indices.forEach { index ->
                val detail = details[index]
                val item = detail?.components?.firstOrNull { holdingKey(it) == key }
                row[index + 1] = when {
                    item?.weight != null -> item.weight
                    detail == null -> null
                    detail.componentsComplete -> "—"
                    else -> null
                }
            }
            comparisonModel.addRow(row)
        }
        values.forEachIndexed { index, etf ->
            val column = comparisonTable.columnModel.getColumn(index + 1)
            column.preferredWidth = 96
            column.minWidth = 76
            val detail = details[index]
            val referenceDate = detail?.let(::componentsReferenceDate)
            column.headerValue = "<html><div style='text-align:center'>${etf.name.take(10).escapeHtml()}<br>${(referenceDate ?: localization.text("기준일 없음", "No date")).escapeHtml()}</div></html>"
            column.headerRenderer = javax.swing.table.DefaultTableCellRenderer().apply {
                toolTipText = localization.text("구성 기준일: ${referenceDate ?: "정보 없음"}", "Holdings as of: ${referenceDate ?: "unknown"}")
            }
            column.cellRenderer = object : javax.swing.table.TableCellRenderer {
                private val panel = JPanel(BorderLayout(2, 0))
                private val label = JLabel()
                private val bar = JProgressBar(0, 1000)
                init {
                    panel.border = JBUI.Borders.empty(1, 3)
                    bar.isStringPainted = false
                    bar.preferredSize = Dimension(10, 4)
                    panel.add(label, BorderLayout.CENTER)
                    panel.add(bar, BorderLayout.SOUTH)
                }
                override fun getTableCellRendererComponent(table: javax.swing.JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): java.awt.Component {
                    label.text = when (value) {
                        is Number -> String.format(Locale.getDefault(), "%.1f%%", value.toDouble())
                        "—" -> "—"
                        else -> localization.text("정보 없음", "N/A")
                    }
                    val weight = (value as? Number)?.toDouble()
                    bar.value = ((weight ?: 0.0).coerceIn(0.0, 100.0) * 10).toInt()
                    bar.isVisible = weight != null
                    panel.background = if (isSelected) table.selectionBackground else table.background
                    label.foreground = if (isSelected) table.selectionForeground else table.foreground
                    return panel
                }
            }
        }
        if (comparisonTable.columnModel.columnCount > 0) {
            comparisonTable.columnModel.getColumn(0).preferredWidth = 142
            comparisonTable.columnModel.getColumn(0).minWidth = 100
        }
        comparisonSummary.text = if (values.isEmpty()) localization.text("비교할 ETF를 추가하세요.", "Add ETFs to compare.") else {
            val concentration = details.mapIndexedNotNull { index, detail ->
                detail?.let {
                    val top5 = topHoldings[index].take(5).sumOf { item -> item.weight ?: 0.0 }
                    val top10 = topHoldings[index].sumOf { item -> item.weight ?: 0.0 }
                    "${values[index].name.take(8)} ${localization.text("상위5", "Top 5")} ${top5.percent()} / ${localization.text("상위10", "Top 10")} ${top10.percent()}"
                }
            }.joinToString(" · ")
            val overlap = when {
                values.size < 2 -> localization.text("비교 ETF를 더 추가하면 공통 종목을 볼 수 있습니다.", "Add another ETF to see shared holdings.")
                else -> localization.text("상위 10개에서 공통 ${sharedKeys.size}종목", "${sharedKeys.size} shared in top 10")
            }
            val weightedOverlap = values.indices.flatMap { left -> (left + 1 until values.size).mapNotNull { right ->
                val leftDetail = details[left] ?: return@mapNotNull null
                val rightDetail = details[right] ?: return@mapNotNull null
                val leftDate = componentsReferenceDate(leftDetail)
                val rightDate = componentsReferenceDate(rightDetail)
                if (!leftDetail.componentsComplete || !rightDetail.componentsComplete || leftDate == null || leftDate != rightDate ||
                    leftDetail.components.any { it.weight == null } || rightDetail.components.any { it.weight == null }) return@mapNotNull null
                val leftWeights = leftDetail.components.mapNotNull { item -> item.weight?.let { holdingKey(item) to it } }.toMap()
                val rightWeights = rightDetail.components.mapNotNull { item -> item.weight?.let { holdingKey(item) to it } }.toMap()
                val common = leftWeights.keys.intersect(rightWeights.keys)
                val overlapWeight = common.sumOf { minOf(leftWeights.getValue(it), rightWeights.getValue(it)) }
                localization.text(
                    "${values[left].name.take(8)}·${values[right].name.take(8)} 가중 중복도 ${overlapWeight.percent()}",
                    "${values[left].name.take(8)}·${values[right].name.take(8)} weighted overlap ${overlapWeight.percent()}"
                )
            } }.joinToString(" · ")
            listOf(overlap, weightedOverlap, concentration).filter { it.isNotBlank() }.joinToString(" · ")
        }
        removeCompare.removeAllItems()
        values.forEach { removeCompare.addItem("${it.code} · ${it.name}") }
        removeCompare.isEnabled = values.isNotEmpty()
        removeCompareButton.isEnabled = values.isNotEmpty()
    }

    private fun distributionChartData(details: List<EtfDetail?>): List<Pair<String, List<Double?>>> {
        val now = LocalDate.now()
        val quarterStartMonth = ((now.monthValue - 1) / 3) * 3 + 1
        val firstQuarter = java.time.YearMonth.of(now.year, quarterStartMonth).minusMonths(15)
        return (0..5).map { offset ->
            val quarter = firstQuarter.plusMonths(offset * 3L)
            val label = "${quarter.year % 100}Q${(quarter.monthValue - 1) / 3 + 1}"
            label to details.map { detail ->
                detail?.distributions?.takeIf { it.isNotEmpty() }?.filter { item ->
                    item.exDividendDate.toLocalDateOrNull()?.let { it.year == quarter.year && (it.monthValue - 1) / 3 == (quarter.monthValue - 1) / 3 } == true
                }?.sumOf { it.amountPerUnit }
            }
        }
    }

    private fun renderAllocations(values: List<EtfSummary>, details: List<EtfDetail?>) {
        val allocations = details.flatMap { it?.allocations.orEmpty() }
        val labels = allocations.map { "${allocationKindLabel(it.kind)} · ${it.name}" }
            .distinct()
            .sortedByDescending { label ->
                allocations.filter { "${allocationKindLabel(it.kind)} · ${it.name}" == label }
                    .maxOfOrNull { it.weight } ?: 0.0
            }
            .take(10)
        allocationModel.setColumnIdentifiers(arrayOf(localization.text("배분 항목", "Allocation")) + values.map { it.name.take(14) }.toTypedArray())
        allocationModel.rowCount = 0
        labels.forEach { itemLabel ->
            val row = arrayOfNulls<Any>(values.size + 1)
            row[0] = itemLabel
            details.forEachIndexed { index, detail ->
                row[index + 1] = detail?.allocations
                    ?.firstOrNull { "${allocationKindLabel(it.kind)} · ${it.name}" == itemLabel }
                    ?.weight?.percent() ?: "—"
            }
            allocationModel.addRow(row)
        }
        allocationReferenceLabel.text = localization.text("배분 기준일", "Allocation as of") + ": " +
            details.mapIndexedNotNull { index, detail ->
                detail?.allocationReferenceDate?.let { "${values[index].name.take(10)} $it" }
            }.joinToString(" · ").ifBlank { localization.text("정보 없음", "Unavailable") }
        if (allocationTable.columnModel.columnCount > 0) {
            allocationTable.columnModel.getColumn(0).preferredWidth = 190
            allocationTable.columnModel.getColumn(0).minWidth = 150
            for (index in 1 until allocationTable.columnModel.columnCount) {
                allocationTable.columnModel.getColumn(index).preferredWidth = 100
                allocationTable.columnModel.getColumn(index).minWidth = 85
            }
        }
    }

    private fun renderComparisonMetrics(values: List<EtfSummary>, details: List<EtfDetail?>) {
        val headers = arrayOf(localization.text("지표", "Metric")) + values.map { it.name.take(14) }.toTypedArray()
        comparisonMetricsModel.setColumnIdentifiers(headers)
        comparisonMetricsModel.rowCount = 0
        val returnPeriods: List<Pair<String, (EtfSummary) -> Double?>> = listOf(
            localization.text("1개월 수익률", "1 month return") to { etf: EtfSummary -> etf.returnRate1m },
            localization.text("3개월 수익률", "3 month return") to { etf: EtfSummary -> etf.returnRate3m },
            localization.text("6개월 수익률", "6 month return") to { etf: EtfSummary -> etf.returnRate6m },
            localization.text("12개월 수익률", "12 month return") to { etf: EtfSummary -> etf.returnRate1y }
        )
        val returnRows = returnPeriods.map { (label, rate) ->
            label to values.mapIndexed { index, etf ->
                (details[index]?.summary?.let(rate) ?: rate(etf)).percent()
            }
        }
        val rows = returnRows + listOf(
            localization.text("총보수", "Total expense ratio") to values.map { it.totalFee.percent() },
            localization.text("순자산", "Net assets") to values.map { it.netAssets.compactAssets() },
            localization.text("거래대금", "Trading value") to values.map { it.tradingValue.compactAssets() },
            localization.text("자금 흐름 1개월", "Fund flow · 1 month") to values.mapIndexed { index, etf -> (details[index]?.summary?.flowMonth ?: etf.flowMonth).flowValue() },
            localization.text("자금 흐름 3개월", "Fund flow · 3 months") to values.mapIndexed { index, etf -> (details[index]?.summary?.flow3m ?: etf.flow3m).flowValue() },
            localization.text("자금 흐름 6개월", "Fund flow · 6 months") to values.mapIndexed { index, etf -> (details[index]?.summary?.flow6m ?: etf.flow6m).flowValue() },
            localization.text("시장가-iNAV 괴리율", "Price-iNAV premium/discount") to values.map { etf ->
                val nav = etf.iNav?.takeIf { it > 0.0 }
                if (nav == null || etf.price == null) localization.text("정보 없음", "N/A")
                else ((etf.price - nav) / nav * 100.0).percent()
            },
            localization.text("최근 분배금 / 기준일", "Latest distribution / ex-date") to details.map { detail ->
                val last = detail?.distributions?.maxByOrNull { it.exDividendDate }
                if (last == null) localization.text("정보 없음", "N/A") else "${last.amountPerUnit.money()}원 · ${last.exDividendDate}"
            },
            localization.text("최근 12개월 분배금", "Trailing 12M distributions") to details.map { detail ->
                (detail?.distributionAmountTtm ?: detail?.let(::trailingDistributionTotal))?.let { "${it.money()}원" } ?: localization.text("정보 없음", "N/A")
            },
            localization.text("최근 12개월 분배율", "Trailing 12M yield") to details.mapIndexed { index, detail ->
                val calculated = detail?.distributionYieldTtm
                    ?: detail?.let(::trailingDistributionTotal)?.let { total -> values[index].price?.takeIf { it > 0 }?.let { total / it * 100 } }
                calculated.percent()
            },
            localization.text("분배 횟수 · 올해", "Distributions · this year") to details.map { detail ->
                detail?.distributionCountThisYear?.let { count -> "$count${localization.text("회", "x")}" }
                    ?: localization.text("정보 없음", "N/A")
            },
            localization.text("분배 월 · 올해", "Distribution months · this year") to details.map { detail ->
                detail?.distributionMonthsThisYear?.takeIf { it.isNotBlank() } ?: localization.text("정보 없음", "N/A")
            },
            localization.text("분배 횟수 · 작년", "Distributions · last year") to details.map { detail ->
                detail?.distributionCountLastYear?.let { count -> "$count${localization.text("회", "x")}" }
                    ?: localization.text("정보 없음", "N/A")
            },
            localization.text("분배금 증가율 (전년 대비)", "Distribution growth (YoY)") to details.map { detail -> distributionGrowth(detail)?.percent() ?: localization.text("이력 부족", "Insufficient history") }
        )
        rows.forEach { (label, items) -> comparisonMetricsModel.addRow(arrayOf(label) + items.toTypedArray()) }
        if (comparisonMetricsTable.columnModel.columnCount > 0) {
            comparisonMetricsTable.columnModel.getColumn(0).preferredWidth = 160
            comparisonMetricsTable.columnModel.getColumn(0).minWidth = 145
            for (index in 1 until comparisonMetricsTable.columnModel.columnCount) {
                comparisonMetricsTable.columnModel.getColumn(index).preferredWidth = 100
                comparisonMetricsTable.columnModel.getColumn(index).minWidth = 88
            }
        }
        comparisonMetricsTable.setDefaultRenderer(Any::class.java, object : javax.swing.table.DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: javax.swing.JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): java.awt.Component {
                val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                label.horizontalAlignment = if (column == 0) JLabel.LEFT else JLabel.RIGHT
                label.border = JBUI.Borders.empty(0, 8)
                label.font = if (column == 0) table.font.deriveFont(java.awt.Font.BOLD) else table.font
                label.foreground = if (isSelected) table.selectionForeground else table.foreground
                return label
            }
        })
    }

    private fun trailingDistributionTotal(detail: EtfDetail): Double? {
        val cutoff = LocalDate.now().minusYears(1)
        val eligible = detail.distributions.filter { it.exDividendDate.toLocalDateOrNull()?.let { date -> date >= cutoff } == true }
        if (eligible.isEmpty() || detail.distributions.none { it.exDividendDate.toLocalDateOrNull()?.let { date -> date <= cutoff } == true }) return null
        return eligible.sumOf { it.amountPerUnit }
    }

    private fun distributionGrowth(detail: EtfDetail?): Double? {
        detail ?: return null
        val today = LocalDate.now()
        val oneYearAgo = today.minusYears(1)
        val twoYearsAgo = today.minusYears(2)
        val dates = detail.distributions.mapNotNull { it.exDividendDate.toLocalDateOrNull() }
        if (dates.minOrNull()?.isAfter(twoYearsAgo) != false) return null
        val recent = detail.distributions.filter { it.exDividendDate.toLocalDateOrNull()?.let { date -> date > oneYearAgo && date <= today } == true }.sumOf { it.amountPerUnit }
        val previous = detail.distributions.filter { it.exDividendDate.toLocalDateOrNull()?.let { date -> date > twoYearsAgo && date <= oneYearAgo } == true }.sumOf { it.amountPerUnit }
        if (previous <= 0.0) return null
        return (recent / previous - 1.0) * 100.0
    }

    private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this.take(10)) }.getOrNull()

    private fun Double?.compactAssets(): String = this?.let {
        when {
            it >= 1_000_000_000_000 -> String.format(Locale.getDefault(), "%.1f조", it / 1_000_000_000_000)
            it >= 100_000_000 -> String.format(Locale.getDefault(), "%.0f억", it / 100_000_000)
            else -> "${money()}원"
        }
    } ?: "—"

    private fun Double?.flowValue(): String = this?.let { String.format(Locale.getDefault(), "%,.0f", it) } ?: "—"

    private fun holdingKey(component: EtfComponent): String = component.code?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { "code:${it}" } ?: "name:${component.name.trim().lowercase(Locale.ROOT)}"

    private fun allocationKindLabel(kind: com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation.Kind): String = when (kind) {
        com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation.Kind.SECTOR -> localization.text("업종", "Sector")
        com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation.Kind.COUNTRY -> localization.text("국가", "Country")
        com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation.Kind.ASSET -> localization.text("자산", "Asset")
    }

    private fun componentsReferenceDate(detail: EtfDetail): String? {
        val dates = detail.components.mapNotNull { it.referenceDate }.distinct()
        return dates.singleOrNull()
    }

    private fun String.escapeHtml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun compactTable(table: JBTable) {
        table.rowHeight = 25
        table.autoResizeMode = JBTable.AUTO_RESIZE_ALL_COLUMNS
        val widths = when (table.columnModel.columnCount) {
            3 -> listOf(145, 80, 72)
            4 -> listOf(145, 78, 58, 58)
            else -> listOf(135, 68, 78, 58).take(table.columnModel.columnCount)
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
        menu.addItem(localization.text("비교에 추가", "Add to Compare")) { addCompare(etf) }
        menu.addItem(localization.text("상세 보기", "Details")) { showDetail(etf) }
        menu.addItem(localization.text("관심 목록에서 제거", "Remove from Watchlist")) { watchlist.remove(etf.code); renderFavorites() }
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

    private fun updateDiscoveryReturnColumn() {
        val period = settings.getEtfDiscoveryReturnPeriod()
        discoveryModel.setColumnIdentifiers(arrayOf(
            localization.text("ETF", "ETF"), localization.text("현재가", "Price"), localization.text("등락률", "Change %"),
            localization.text("${period.labelKo} 수익률", "${period.labelEn} return")
        ))
    }

    private fun fill(model: DefaultTableModel, values: List<EtfSummary>) {
        renderedEtfs[model] = values
        model.rowCount = 0
        values.forEach { e ->
            val cells = when (model.columnCount) {
                3 -> arrayOf(e.name, e.price.money(), e.changeRate.percent())
                4 -> when (model) {
                    discoveryModel -> arrayOf(e.name, e.price.money(), e.changeRate.percent(), settings.getEtfDiscoveryReturnPeriod().rate(e).percent())
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

private class EtfComparisonBarChart(
    private val centered: Boolean,
    private val valueSuffix: String,
    private val emptyMessage: String
) : JPanel() {
    private var seriesNames: List<String> = emptyList()
    private var categories: List<Pair<String, List<Double?>>> = emptyList()
    private val colors = listOf(
        JBColor(Color(0x3574F0), Color(0x548AF7)),
        JBColor(Color(0x159B8A), Color(0x46C2B2)),
        JBColor(Color(0xD98E04), Color(0xE7A93B)),
        JBColor(Color(0x8756C7), Color(0xB18AE8))
    )

    init {
        isOpaque = true
        background = JBColor.PanelBackground
        foreground = JBColor.foreground()
        preferredSize = Dimension(540, 300)
    }

    fun updateData(names: List<String>, values: List<Pair<String, List<Double?>>>) {
        seriesNames = names
        categories = values
        repaint()
    }

    override fun paintComponent(graphics: java.awt.Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as java.awt.Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.font = javax.swing.UIManager.getFont("Label.font") ?: font
            g.color = foreground
            val values = categories.flatMap { it.second }.filterNotNull()
            if (seriesNames.isEmpty() || values.isEmpty()) {
                val message = if (seriesNames.isEmpty()) emptyMessage else emptyMessage
                g.drawString(message, 16, 28)
                return
            }

            var legendX = 12
            val legendY = 20
            seriesNames.forEachIndexed { index, name ->
                g.color = colors[index % colors.size]
                g.fillRoundRect(legendX, legendY - 9, 9, 9, 3, 3)
                g.color = foreground
                val shortName = if (name.length > 12) name.take(11) + "…" else name
                g.drawString(shortName, legendX + 14, legendY)
                legendX += 22 + g.fontMetrics.stringWidth(shortName)
            }

            val left = 82
            val right = 62
            val top = 38
            val bottom = 22
            val plotWidth = (width - left - right).coerceAtLeast(80)
            val plotHeight = (height - top - bottom).coerceAtLeast(80)
            val rowHeight = plotHeight.toFloat() / categories.size.coerceAtLeast(1)
            val maxValue = values.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1.0)
            val axisX = if (centered) left + plotWidth / 2 else left
            val scale = if (centered) (plotWidth / 2.0 - 30).coerceAtLeast(1.0) / maxValue else (plotWidth - 50.0).coerceAtLeast(1.0) / maxValue
            val gridColor = JBColor(Color(0xE5E7EB), Color(0x45494A))
            g.color = gridColor
            if (centered) {
                g.drawLine(axisX, top, axisX, top + plotHeight)
                g.drawLine(left, top + plotHeight, width - right, top + plotHeight)
            } else {
                for (tick in 0..4) {
                    val x = left + (plotWidth * tick / 4)
                    g.drawLine(x, top, x, top + plotHeight)
                }
            }

            categories.forEachIndexed { categoryIndex, (label, categoryValues) ->
                val rowTop = top + (rowHeight * categoryIndex).toInt()
                val rowCenter = rowTop + (rowHeight / 2).toInt()
                g.color = foreground
                g.drawString(label, 10, rowCenter + g.fontMetrics.ascent / 2 - 1)
                val barHeight = (rowHeight / (seriesNames.size + 1)).toInt().coerceIn(4, 12)
                val totalBarsHeight = seriesNames.size * (barHeight + 2)
                val firstY = rowCenter - totalBarsHeight / 2
                seriesNames.indices.forEach { seriesIndex ->
                    val value = categoryValues.getOrNull(seriesIndex) ?: return@forEach
                    val y = firstY + seriesIndex * (barHeight + 2)
                    val barWidth = (kotlin.math.abs(value) * scale).toInt().coerceAtLeast(if (value == 0.0) 1 else 2)
                    val x = if (centered && value < 0) axisX - barWidth else axisX
                    g.color = colors[seriesIndex % colors.size]
                    g.fillRoundRect(x, y, barWidth, barHeight, 4, 4)
                    val labelText = String.format(Locale.getDefault(), "%.1f%s", value, valueSuffix)
                    g.color = foreground
                    val textWidth = g.fontMetrics.stringWidth(labelText)
                    val textX = when {
                        centered && value < 0 -> (x - textWidth - 4).coerceAtLeast(left)
                        else -> (x + barWidth + 4).coerceAtMost(width - right - textWidth)
                    }
                    g.drawString(labelText, textX, y + barHeight - 1)
                }
            }
        } finally {
            g.dispose()
        }
    }
}
