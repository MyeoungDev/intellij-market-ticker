package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.listener.TickerUpdateListener
import com.github.myeoungdev.marketticker.application.listener.WatchlistEntryUpdateListener
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.application.service.MarketHoursPolicy
import com.github.myeoungdev.marketticker.application.service.MoneyDisplayFormatter
import com.github.myeoungdev.marketticker.application.service.PriceAlertService
import com.github.myeoungdev.marketticker.application.service.PriceRefreshSource
import com.github.myeoungdev.marketticker.common.extenion.parseCommaToDouble
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.github.myeoungdev.marketticker.ui.alert.AlertSettingsDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComboBox
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn

class WatchlistView(private val project: Project) {

    private val marketDataService = service<MarketDataService>()
    private val appSettingsService = service<AppSettingsService>()
    private val alertService = service<PriceAlertService>()
    private val localizationService = service<LocalizationService>()
    private val moneyDisplayFormatter = MoneyDisplayFormatter()

    private var currentWatchlistEntries: List<WatchlistRepository.WatchlistEntry> = emptyList()
    private var filteredEntries: List<WatchlistRepository.WatchlistEntry> = emptyList()
    private var currentPrices: List<TickerPrice> = emptyList()
    private var marketSessionColumn: TableColumn? = null

    val panel = JPanel(BorderLayout())

    private val tableModel =
        object : DefaultTableModel(
            arrayOf(
                localizationService.text("종목명", "Symbol"),
                localizationService.text("현재가", "Price"),
                localizationService.text("등락값", "Change Amt"),
                localizationService.text("등락률", "Change"),
                localizationService.text("그룹", "Group"),
                localizationService.text("시장", "Mkt"),
                localizationService.text("알람", "Alert")
            ),
            0
        ) {
            override fun isCellEditable(row: Int, column: Int) = false
        }

    private val tickerTable = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = 30
        emptyText.text = localizationService.text("관심 종목이 없습니다. 검색을 통해 추가하세요.", "Watchlist is empty. Search and add symbols.")
    }

    private val groupFilter = JComboBox<String>()

    var onTickerSelected: ((Ticker, TickerPrice?) -> Unit)? = null

    init {
        setupUI()
        subscribeToTickerUpdates()
        loadInitialData()
    }

    private fun loadInitialData() {
        currentWatchlistEntries = marketDataService.getWatchlistEntries()
        updateGroupFilter()
        if (appSettingsService.isAutomaticPollingEnabled()) {
            marketDataService.refreshPricesAsync(PriceRefreshSource.STARTUP)
        }
    }

    private fun setupUI() {
        tickerTable.setDefaultRenderer(Object::class.java, PriceCellRenderer())

        tickerTable.columnModel.getColumn(0).preferredWidth = 115
        tickerTable.columnModel.getColumn(1).preferredWidth = 95
        tickerTable.columnModel.getColumn(2).preferredWidth = 95
        tickerTable.columnModel.getColumn(3).preferredWidth = 70
        tickerTable.columnModel.getColumn(4).preferredWidth = 80
        tickerTable.columnModel.getColumn(5).apply {
            maxWidth = 42
            minWidth = 36
            preferredWidth = 38
            cellRenderer = MarketSessionRenderer()
            marketSessionColumn = this
        }
        tickerTable.columnModel.getColumn(tableModel.columnCount - 1).apply {
            maxWidth = 50
            minWidth = 50
            cellRenderer = AlertIconRenderer()
        }

        val popupMenu = JPopupMenu()
        val editPortfolioItem = JMenuItem(localizationService.text("포트폴리오 등록/편집", "Add/Edit portfolio"))
        val editTagItem = JMenuItem(localizationService.text("그룹 편집", "Edit group"))
        val deleteTickerItem = JMenuItem(localizationService.text("관심 종목 삭제", "Remove from watchlist"))

        editPortfolioItem.addActionListener {
            val entry = getSelectedWatchlistEntry() ?: return@addActionListener
            val dialog = PortfolioEditDialog(entry)
            if (dialog.showAndGet()) {
                val updatedEntry = entry.copy(
                    purchasePrice = dialog.purchasePrice,
                    quantity = dialog.quantity
                )
                marketDataService.updateWatchlistEntryPortfolio(updatedEntry)
            }
        }

        editTagItem.addActionListener {
            val selectedViewRow = tickerTable.selectedRow
            if (selectedViewRow == -1) return@addActionListener
            val modelRow = tickerTable.convertRowIndexToModel(selectedViewRow)
            val entry = getWatchlistEntryAtRow(modelRow) ?: return@addActionListener

            val value = Messages.showInputDialog(
                project,
                localizationService.text("그룹을 입력하세요 (예: 국내, 반도체, 테마)", "Enter group (e.g., KR, semiconductor, theme)"),
                localizationService.text("그룹 편집", "Edit group"),
                null,
                entry.groupTag,
                null
            ) ?: return@addActionListener

            marketDataService.updateWatchlistEntryPortfolio(entry.copy(groupTag = value.trim()))
        }

        deleteTickerItem.addActionListener {
            val selectedViewRow = tickerTable.selectedRow
            if (selectedViewRow != -1) {
                val modelRow = tickerTable.convertRowIndexToModel(selectedViewRow)
                val entry = getWatchlistEntryAtRow(modelRow)
                if (entry != null) {
                    marketDataService.removeTicker(entry.symbol, MarketType.of(entry.marketType))
                }
            }
        }

        popupMenu.add(editPortfolioItem)
        popupMenu.add(editTagItem)
        popupMenu.add(deleteTickerItem)

        tickerTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val viewRow = tickerTable.rowAtPoint(e.point)
                val viewColumn = tickerTable.columnAtPoint(e.point)

                if (viewRow < 0 || viewColumn < 0) return

                val modelRow = tickerTable.convertRowIndexToModel(viewRow)
                val modelColumn = tickerTable.convertColumnIndexToModel(viewColumn)
                val entry = getWatchlistEntryAtRow(modelRow) ?: return

                if (modelColumn == tableModel.columnCount - 1) {
                    val ticker = Ticker(
                        entry.symbol,
                        entry.tradingSymbol,
                        entry.name,
                        MarketType.of(entry.marketType),
                        entry.nationCode,
                        entry.nationName
                    )
                    if (AlertSettingsDialog(ticker).showAndGet()) {
                        tickerTable.repaint()
                    }
                } else if (e.clickCount == 2) {
                    val selectedPrice = currentPrices.find {
                        it.symbol == entry.symbol && it.marketType == MarketType.of(entry.marketType)
                    }
                    val ticker = Ticker(
                        entry.symbol,
                        entry.tradingSymbol,
                        entry.name,
                        MarketType.of(entry.marketType),
                        entry.nationCode,
                        entry.nationName
                    )
                    onTickerSelected?.invoke(ticker, selectedPrice)
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
                        tickerTable.requestFocusInWindow()
                        popupMenu.show(e.component, e.x, e.y)
                    }
                }
            }
        })

        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        groupFilter.addItem(localizationService.text("전체", "All"))
        groupFilter.addActionListener { updateTable() }
        filterPanel.add(groupFilter)

        panel.add(filterPanel, BorderLayout.NORTH)
        panel.add(JBScrollPane(tickerTable), BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(5)
        updateMarketSessionColumnVisibility()
    }

    private fun subscribeToTickerUpdates() {
        val connection = project.messageBus.connect()

        connection.subscribe(TickerUpdateListener.TOPIC, object : TickerUpdateListener {
            override fun onTickerUpdated(prices: List<TickerPrice>) {
                ApplicationManager.getApplication().invokeLater {
                    currentPrices = prices
                    updateTable()
                }
            }
        })

        connection.subscribe(WatchlistEntryUpdateListener.TOPIC, object : WatchlistEntryUpdateListener {
            override fun onWatchlistEntryUpdated() {
                ApplicationManager.getApplication().invokeLater {
                    currentWatchlistEntries = marketDataService.getWatchlistEntries()
                    updateGroupFilter()
                    updateTable()
                }
            }
        })

        connection.subscribe(SettingsUpdateListener.TOPIC, object : SettingsUpdateListener {
            override fun onSettingsUpdated() {
                ApplicationManager.getApplication().invokeLater {
                    updateMarketSessionColumnVisibility()
                    tickerTable.repaint()
                }
            }
        })
    }

    private fun updateMarketSessionColumnVisibility() {
        val column = marketSessionColumn ?: return
        val visible = tickerTable.columnModel.columns.asSequence().any { it === column }
        val shouldShow = appSettingsService.isMarketSessionIndicatorVisible()

        if (shouldShow && !visible) {
            tickerTable.columnModel.addColumn(column)
            val lastViewIndex = tickerTable.columnModel.columnCount - 1
            val beforeAlertIndex = (lastViewIndex - 1).coerceAtLeast(0)
            tickerTable.columnModel.moveColumn(lastViewIndex, beforeAlertIndex)
        } else if (!shouldShow && visible) {
            tickerTable.columnModel.removeColumn(column)
        }
    }

    private fun updateGroupFilter() {
        val selected = groupFilter.selectedItem as? String
        val all = localizationService.text("전체", "All")

        val tags = currentWatchlistEntries.map { it.groupTag.ifBlank { defaultGroup(it) } }
            .distinct()
            .sorted()

        groupFilter.removeAllItems()
        groupFilter.addItem(all)
        tags.forEach { groupFilter.addItem(it) }

        if (selected != null && tags.contains(selected)) {
            groupFilter.selectedItem = selected
        } else {
            groupFilter.selectedItem = all
        }
    }

    private fun updateTable() {
        ApplicationManager.getApplication().invokeLater {
            tableModel.rowCount = 0

            val selectedFilter = (groupFilter.selectedItem as? String).orEmpty()
            val all = localizationService.text("전체", "All")

            filteredEntries = currentWatchlistEntries.filter { entry ->
                val group = entry.groupTag.ifBlank { defaultGroup(entry) }
                selectedFilter == all || selectedFilter.isBlank() || selectedFilter == group
            }

            filteredEntries.forEach { entry ->
                val price = currentPrices.find {
                    it.symbol == entry.symbol && it.marketType == MarketType.of(entry.marketType)
                }

                val currentPrice = price?.currentPrice
                val changeRate = price?.changeRate ?: 0.0
                val changeAmount = price?.changeAmount
                val sign = if (changeRate > 0) "+" else ""
                val rateText = "$sign${localizationService.formatDecimal(changeRate, 2)}%"

                tableModel.addRow(
                    arrayOf(
                        entry.name,
                        moneyDisplayFormatter.formatAmount(currentPrice, price?.currency),
                        moneyDisplayFormatter.formatSignedAmount(changeAmount, null),
                        rateText,
                        entry.groupTag.ifBlank { defaultGroup(entry) },
                        "",
                        ""
                    )
                )
            }

            tickerTable.repaint()
        }
    }

    fun updateWith(prices: List<TickerPrice>) {
        currentPrices = prices
        currentWatchlistEntries = marketDataService.getWatchlistEntries()
        updateTable()
    }

    private fun getWatchlistEntryAtRow(row: Int): WatchlistRepository.WatchlistEntry? {
        return filteredEntries.getOrNull(row)
    }

    private fun getSelectedWatchlistEntry(): WatchlistRepository.WatchlistEntry? {
        val selectedViewRow = tickerTable.selectedRow
        if (selectedViewRow < 0) return null
        val modelRow = tickerTable.convertRowIndexToModel(selectedViewRow)
        return getWatchlistEntryAtRow(modelRow)
    }

    private fun defaultGroup(entry: WatchlistRepository.WatchlistEntry): String {
        return when {
            MarketType.of(entry.marketType).isKoreanMarket() -> localizationService.text("국내", "Domestic")
            MarketType.of(entry.marketType).isGlobalStockMarket() -> localizationService.text("해외", "Global")
            else -> localizationService.text("기타", "Other")
        }
    }

    private fun WatchlistRepository.WatchlistEntry.toTicker(): Ticker {
        return Ticker(
            symbol = symbol,
            tradingSymbol = tradingSymbol.ifBlank { symbol },
            name = name,
            marketType = MarketType.of(marketType),
            nationCode = nationCode,
            nationName = nationName
        )
    }

    private fun isPollableNow(entry: WatchlistRepository.WatchlistEntry): Boolean {
        return MarketHoursPolicy.isPollable(
            ticker = entry.toTicker(),
            domesticVenueMode = appSettingsService.getDomesticTradeVenueMode()
        )
    }

    inner class AlertIconRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)

            val modelRow = table?.convertRowIndexToModel(row) ?: row
            val entry = getWatchlistEntryAtRow(modelRow)
            val hasAlert = entry != null && alertService.getAlert(entry.symbol) != null

            icon = if (hasAlert) {
                AllIcons.Toolwindows.NotificationsNew
            } else {
                AllIcons.Toolwindows.Notifications
            }

            horizontalAlignment = CENTER
            return this
        }
    }

    private inner class MarketSessionRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, "●", isSelected, hasFocus, row, column)
            if (table == null) return c

            val modelRow = table.convertRowIndexToModel(row)
            val entry = getWatchlistEntryAtRow(modelRow)
            val pollable = entry?.let(::isPollableNow)

            horizontalAlignment = CENTER
            foreground = when (pollable) {
                true -> JBColor(Color(76, 175, 80), Color(46, 125, 50))
                false -> JBColor(Color(229, 57, 53), Color(198, 40, 40))
                null -> JBColor.GRAY
            }
            toolTipText = when (pollable) {
                true -> localizationService.text("자동 폴링 대상입니다.", "Eligible for automatic polling.")
                false -> localizationService.text("장외 시간으로 자동 폴링이 일시 중지됩니다.", "Automatic polling is paused outside market hours.")
                null -> null
            }
            return c
        }
    }

    private inner class PriceCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (table == null) return c

            val modelColumn = table.convertColumnIndexToModel(column)
            background = if (isSelected) table.selectionBackground else table.background
            if (modelColumn in setOf(2, 3) && value is String) {
                val numeric = value.parseCommaToDouble()
                foreground = when {
                    numeric > 0 -> JBColor.RED
                    numeric < 0 -> JBColor.BLUE
                    else -> if (isSelected) table.selectionForeground else table.foreground
                }
            } else {
                foreground = if (isSelected) table.selectionForeground else table.foreground
            }
            return c
        }
    }
}
