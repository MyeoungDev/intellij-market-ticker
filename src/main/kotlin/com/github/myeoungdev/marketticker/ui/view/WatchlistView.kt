package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.TickerUpdateListener
import com.github.myeoungdev.marketticker.application.listener.WatchlistEntryUpdateListener
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.application.service.PriceAlertService
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.github.myeoungdev.marketticker.ui.alert.AlertSettingsDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * 관심 종목 View 를 나타내는 클래스
 *
 * @author  : 강명관
 * @since   : 2025-12-02
 */

private val logger = KotlinLogging.logger {}

class WatchlistView(private val project: Project) {

    private val marketDataService = service<MarketDataService>()
    private val alertService = service<PriceAlertService>()

    private var currentWatchlistEntries: List<WatchlistRepository.WatchlistEntry> = emptyList()
    private var currentPrices: List<TickerPrice> = emptyList() // Still need this to map prices to entries

    val panel = JPanel(BorderLayout())

    private val tableModel =
        object : DefaultTableModel(arrayOf("종목명", "현재가", "등락률", "알람"), 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }

    private val tickerTable = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = 30
        emptyText.text = "관심 종목이 없습니다. 검색을 통해 추가하세요."
    }

    // Ticker 선택 리스너
    var onTickerSelected: ((Ticker, TickerPrice?) -> Unit)? = null

    init {
        setupUI()
        subscribeToTickerUpdates()
        loadInitialData()
    }

    private fun loadInitialData() {
        val initialEntries = marketDataService.getWatchlistEntries()
        if (initialEntries.isNotEmpty()) {
            logger.info { "Loading initial watchlist entries: ${initialEntries.size}" }
            currentWatchlistEntries = initialEntries
            marketDataService.forceRefresh() 
        } else {
            logger.info { "Watchlist empty. Requesting force refresh..." }
            marketDataService.forceRefresh()
        }
    }

    private fun setupUI() {
        // 테이블 설정
        tickerTable.setDefaultRenderer(Object::class.java, PriceCellRenderer())

        // 알람 아이콘 컬럼 설정 (마지막 컬럼)
        tickerTable.columnModel.getColumn(tableModel.columnCount - 1).apply {
            maxWidth = 40
            minWidth = 40
            cellRenderer = AlertIconRenderer()
        }

        // 컨텍스트 메뉴 추가
        val popupMenu = JPopupMenu()
        val editPortfolioItem = JMenuItem("포트폴리오 정보 편집")
        val deleteTickerItem = JMenuItem("관심 종목 삭제")

        editPortfolioItem.addActionListener {
            val selectedViewRow = tickerTable.selectedRow
            if (selectedViewRow != -1) {
                val modelRow = tickerTable.convertRowIndexToModel(selectedViewRow)
                val entry = getWatchlistEntryAtRow(modelRow)
                if (entry != null) {
                    val dialog = PortfolioEditDialog(entry)
                    if (dialog.showAndGet()) {
                        val updatedEntry = entry.copy(
                            purchasePrice = dialog.purchasePrice,
                            quantity = dialog.quantity
                        )
                        marketDataService.updateWatchlistEntryPortfolio(updatedEntry)
                    }
                }
            }
        }

        deleteTickerItem.addActionListener {
            val selectedViewRow = tickerTable.selectedRow
            if (selectedViewRow != -1) {
                val modelRow = tickerTable.convertRowIndexToModel(selectedViewRow)
                val entry = getWatchlistEntryAtRow(modelRow)
                if (entry != null) {
                    marketDataService.removeTicker(entry.symbol)
                }
            }
        }

        popupMenu.add(editPortfolioItem)
        popupMenu.add(deleteTickerItem)

        tickerTable.componentPopupMenu = popupMenu

        // 클릭 이벤트 리스너 (알람 설정 다이얼로그)
        tickerTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val viewRow = tickerTable.rowAtPoint(e.point)
                val viewColumn = tickerTable.columnAtPoint(e.point)

                if (viewRow < 0) return

                val modelRow = tickerTable.convertRowIndexToModel(viewRow)
                val entry = getWatchlistEntryAtRow(modelRow) ?: return

                // 알람 아이콘 컬럼 클릭 시 알람 설정 다이얼로그 열기
                if (viewColumn == tableModel.columnCount - 1) { // 마지막 컬럼이 알람 컬럼
                    val ticker = Ticker(
                        entry.symbol, entry.tradingSymbol, entry.name,
                        MarketType.valueOf(entry.marketType), entry.nationCode, entry.nationName
                    )
                    if (AlertSettingsDialog(ticker).showAndGet()) {
                        tickerTable.repaint()
                    }
                } else if (e.clickCount == 2) {
                    // 차트 뷰 업데이트를 위한 더블클릭 (알람 컬럼 제외)
                    val selectedEntry = getWatchlistEntryAtRow(modelRow)
                    val selectedPrice = currentPrices.find {
                        it.symbol == selectedEntry?.symbol && it.marketType == MarketType.valueOf(selectedEntry.marketType)
                    }
                    if (selectedEntry != null) {
                        val ticker = Ticker(
                            selectedEntry.symbol,
                            selectedEntry.tradingSymbol,
                            selectedEntry.name,
                            MarketType.valueOf(selectedEntry.marketType),
                            selectedEntry.nationCode,
                            selectedEntry.nationName
                        )
                        onTickerSelected?.invoke(ticker, selectedPrice)
                    }
                }
            }

            override fun mousePressed(e: MouseEvent) {
                maybeShowPopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                maybeShowPopup(e)
            }

            private fun maybeShowPopup(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val row = tickerTable.rowAtPoint(e.point)
                    if (row != -1) {
                        tickerTable.setRowSelectionInterval(row, row)
                    }
                    popupMenu.show(e.component, e.x, e.y)
                }
            }
        })

        // Ticker 선택 변경 리스너 (차트 뷰 업데이트용)
        tickerTable.selectionModel.addListSelectionListener(object : ListSelectionListener {
            override fun valueChanged(e: ListSelectionEvent?) {
                if (e?.valueIsAdjusting == false) {
                    val selectedViewRow = tickerTable.selectedRow
                    if (selectedViewRow != -1) {
                        val modelRow = tickerTable.convertRowIndexToModel(selectedViewRow)
                        val selectedEntry = getWatchlistEntryAtRow(modelRow)
                        val selectedPrice = currentPrices.find {
                            it.symbol == selectedEntry?.symbol && it.marketType == selectedEntry.marketType.let { mt -> MarketType.valueOf(mt) }
                        }
                        if (selectedEntry != null) {
                            // Convert WatchlistEntry to Ticker for onTickerSelected callback
                            val ticker = Ticker(
                                selectedEntry.symbol,
                                selectedEntry.tradingSymbol,
                                selectedEntry.name,
                                MarketType.valueOf(selectedEntry.marketType),
                                selectedEntry.nationCode,
                                selectedEntry.nationName
                            )
                            onTickerSelected?.invoke(ticker, selectedPrice)
                        }
                    }
                }
            }
        })

        panel.add(JBScrollPane(tickerTable), BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(5)
    }

    private fun subscribeToTickerUpdates() {
        val connection = project.messageBus.connect()

        connection.subscribe(TickerUpdateListener.TOPIC, object : TickerUpdateListener {
            override fun onTickerUpdated(prices: List<TickerPrice>) {
                // UI 스레드 보장
                ApplicationManager.getApplication().invokeLater {
                    currentPrices = prices // Update current prices
                    updateTable() // Update table using currentWatchlistEntries and currentPrices
                }
            }
        })

        connection.subscribe(WatchlistEntryUpdateListener.TOPIC, object : WatchlistEntryUpdateListener {
            override fun onWatchlistEntryUpdated() {
                // UI 스레드 보장
                ApplicationManager.getApplication().invokeLater {
                    // Watchlist entry itself changed, not necessarily prices.
                    // Need to re-fetch entries and update table.
                    currentWatchlistEntries = marketDataService.getWatchlistEntries()
                    updateTable()
                }
            }
        })
    }

    // Modified updateTable to use currentWatchlistEntries and currentPrices
    private fun updateTable() {
        // UI 갱신은 반드시 EDT에서 수행
        ApplicationManager.getApplication().invokeLater {
            // 기존 데이터 삭제
            tableModel.rowCount = 0

            currentWatchlistEntries.forEach { entry ->
                val price = currentPrices.find {
                    it.symbol == entry.symbol && it.marketType == MarketType.valueOf(entry.marketType)
                }

                val currentPrice = price?.currentPrice
                val changeRate = price?.changeRate ?: 0.0

                val sign = if (changeRate > 0) "+" else ""
                val rateText = "$sign${String.format("%.2f", changeRate)}%"

                tableModel.addRow(
                    arrayOf(
                        entry.name,
                        currentPrice?.let { String.format("%,.2f", it) } ?: "-",
                        rateText,
                        "" // 알람 아이콘 컬럼
                    )
                )
            }

            // 렌더러가 알람 상태 등도 다시 그리도록 트리거
            tickerTable.repaint()
        }
    }

    // Modified updateWith to use currentWatchlistEntries and currentPrices
    fun updateWith(prices: List<TickerPrice>) {
        currentPrices = prices // Update current prices
        currentWatchlistEntries = marketDataService.getWatchlistEntries() // Refresh entries from repository
        updateTable() // Call the unified updateTable
    }

    // New method to get WatchlistEntry at a given row
    private fun getWatchlistEntryAtRow(row: Int): WatchlistRepository.WatchlistEntry? {
        return currentWatchlistEntries.getOrNull(row)
    }

    // Removed getTickerFromPrice and getTickerAtRow

    inner class AlertIconRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)

            val entry = getWatchlistEntryAtRow(row)
            // Convert WatchlistEntry to Ticker for alertService
            val ticker = entry?.let {
                Ticker(
                    it.symbol,
                    it.tradingSymbol,
                    it.name,
                    MarketType.valueOf(it.marketType),
                    it.nationCode,
                    it.nationName
                )
            }
            val hasAlert = ticker != null && alertService.getAlert(ticker.tradingSymbol) != null

            icon = if (hasAlert) {
                AllIcons.Toolwindows.NotificationsNew // 설정됨 (채워진 종)
            } else {
                AllIcons.Toolwindows.Notifications // 미설정 (빈 종)
            }

            horizontalAlignment = CENTER
            return this
        }
    }

    private class PriceCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

            // Assuming change rate is in column 2
            if (column == 2 && value is String) {
                foreground = when {
                    value.startsWith("+") -> JBColor.RED // 상승
                    value.startsWith("-") -> JBColor.BLUE // 하락
                    else -> JBColor.BLACK // 보합
                }
            } else {
                foreground = if (isSelected) table?.selectionForeground else table?.foreground
            }
            return c
        }
    }
}
