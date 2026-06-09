package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.WatchlistEntryUpdateListener
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.application.service.MoneyDisplayFormatter
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class PortfolioView {

    private val marketDataService = service<MarketDataService>()
    private val localizationService = service<LocalizationService>()
    private val moneyDisplayFormatter = MoneyDisplayFormatter()

    val panel = JPanel(BorderLayout())
    private val tableModel =
        object : DefaultTableModel(
            arrayOf(
                localizationService.text("종목명", "Symbol"),
                localizationService.text("수량", "Qty"),
                localizationService.text("매수평균", "Avg Buy"),
                localizationService.text("현재가", "Price"),
                localizationService.text("평가금액", "Market Value"),
                localizationService.text("평가손익", "PnL"),
                localizationService.text("수익률", "Return")
            ),
            0
        ) {
            override fun isCellEditable(row: Int, column: Int) = false
        }

    private val portfolioTable = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = 30
        emptyText.text = localizationService.text(
            "포트폴리오에 종목이 없습니다. 관심종목에서 포트폴리오 정보를 추가하세요.",
            "Portfolio is empty. Add portfolio data from watchlist."
        )
    }

    private var currentWatchlistEntries: List<WatchlistRepository.WatchlistEntry> = emptyList()
    private var currentPrices: List<TickerPrice> = emptyList()
    private var portfolioEntries: List<WatchlistRepository.WatchlistEntry> = emptyList()

    var onTickerSelected: ((Ticker, TickerPrice?) -> Unit)? = null

    init {
        setupUI()
        loadInitialData()
        subscribeToWatchlistEntryUpdates()
    }

    private fun subscribeToWatchlistEntryUpdates() {
        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(WatchlistEntryUpdateListener.TOPIC, object : WatchlistEntryUpdateListener {
            override fun onWatchlistEntryUpdated() {
                ApplicationManager.getApplication().invokeLater {
                    currentWatchlistEntries = marketDataService.getWatchlistEntries()
                    updateTable()
                }
            }
        })
    }

    private fun setupUI() {
        portfolioTable.setDefaultRenderer(Object::class.java, PriceCellRenderer())
        val popupMenu = JPopupMenu()
        val editPortfolioItem = JMenuItem(localizationService.text("포트폴리오 정보 편집", "Edit portfolio"))
        val removePortfolioItem = JMenuItem(localizationService.text("포트폴리오에서 삭제", "Remove from portfolio"))

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

        removePortfolioItem.addActionListener {
            val entry = getSelectedWatchlistEntry() ?: return@addActionListener
            val confirmed = Messages.showYesNoDialog(
                panel,
                localizationService.text(
                    "${entry.name}의 포트폴리오 정보를 삭제할까요?\n관심종목에서는 삭제되지 않습니다.",
                    "Remove portfolio data for ${entry.name}?\nThe ticker will remain in the watchlist."
                ),
                localizationService.text("포트폴리오 삭제", "Remove Portfolio"),
                Messages.getQuestionIcon()
            )
            if (confirmed == Messages.YES) {
                marketDataService.removePortfolioEntry(entry)
            }
        }

        portfolioTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = portfolioTable.rowAtPoint(e.point)
                if (row < 0) return
                selectRow(row)

                if (e.clickCount == 2) {
                    val entry = getSelectedWatchlistEntry() ?: return
                    val ticker = entry.toTicker()
                    val selectedPrice = currentPrices.find {
                        it.symbol == entry.symbol && it.marketType == MarketType.of(entry.marketType)
                    }
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
                if (!e.isPopupTrigger) return
                val row = portfolioTable.rowAtPoint(e.point)
                if (row < 0) return
                selectRow(row)
                popupMenu.show(e.component, e.x, e.y)
            }
        })

        popupMenu.add(editPortfolioItem)
        popupMenu.add(removePortfolioItem)
        panel.add(JBScrollPane(portfolioTable), BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(5)
    }

    private fun loadInitialData() {
        val initialEntries = marketDataService.getWatchlistEntries()
        if (initialEntries.isNotEmpty()) {
            currentWatchlistEntries = initialEntries
            updateTable()
        }
    }

    fun updateWith(prices: List<TickerPrice>) {
        currentPrices = prices
        currentWatchlistEntries = marketDataService.getWatchlistEntries()
        updateTable()
    }

    private fun updateTable() {
        ApplicationManager.getApplication().invokeLater {
            tableModel.rowCount = 0

            val portfolioRows = currentWatchlistEntries
                .mapNotNull { entry ->
                    val quantity = entry.quantity ?: return@mapNotNull null
                    val purchasePrice = entry.purchasePrice ?: return@mapNotNull null
                    if (quantity <= 0 || purchasePrice <= 0) return@mapNotNull null

                    val price = currentPrices.find {
                        it.symbol == entry.symbol && it.marketType == MarketType.of(entry.marketType)
                    }
                    val currentPrice = price?.currentPrice ?: purchasePrice
                    val marketValue = currentPrice * quantity
                    val unrealized = (currentPrice - purchasePrice) * quantity
                    Triple(entry, price, PortfolioMetrics(marketValue, unrealized))
                }
            portfolioEntries = portfolioRows.map { it.first }

            portfolioRows.forEach { (entry, price, metrics) ->
                val returnRate = if ((entry.purchasePrice ?: 0.0) > 0.0) {
                    ((price?.currentPrice ?: entry.purchasePrice ?: 0.0) - (entry.purchasePrice ?: 0.0)) /
                        (entry.purchasePrice ?: 1.0) * 100.0
                } else {
                    0.0
                }

                    tableModel.addRow(
                    arrayOf(
                        entry.name,
                        localizationService.formatDecimal(entry.quantity ?: 0.0, 2),
                        moneyDisplayFormatter.formatAmount(entry.purchasePrice, price?.currency),
                        moneyDisplayFormatter.formatAmount(price?.currentPrice, price?.currency),
                        moneyDisplayFormatter.formatAmount(metrics.marketValue, price?.currency ?: CurrencyType.UNKNOWN),
                        moneyDisplayFormatter.formatAmount(metrics.unrealized, price?.currency ?: CurrencyType.UNKNOWN),
                        localizationService.formatPercent(returnRate)
                    )
                )
            }

            portfolioTable.repaint()
        }
    }

    private fun getWatchlistEntryAtRow(row: Int): WatchlistRepository.WatchlistEntry? {
        return portfolioEntries.getOrNull(row)
    }

    private fun getSelectedWatchlistEntry(): WatchlistRepository.WatchlistEntry? {
        val selectedViewRow = portfolioTable.selectedRow
        if (selectedViewRow < 0) return null
        return getWatchlistEntryAtRow(portfolioTable.convertRowIndexToModel(selectedViewRow))
    }

    private fun selectRow(viewRow: Int) {
        portfolioTable.setRowSelectionInterval(viewRow, viewRow)
        portfolioTable.requestFocusInWindow()
    }

    private fun WatchlistRepository.WatchlistEntry.toTicker(): Ticker {
        return Ticker(
            symbol = symbol,
            tradingSymbol = tradingSymbol,
            name = name,
            marketType = MarketType.of(marketType),
            nationCode = nationCode,
            nationName = nationName
        )
    }

    private data class PortfolioMetrics(
        val marketValue: Double,
        val unrealized: Double
    )

    private class PriceCellRenderer : DefaultTableCellRenderer() {
        private val signedColumns = setOf(5, 6)

        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (table == null) return c

            c.foreground = if (isSelected) table.selectionForeground else table.foreground

            if (column in signedColumns && value is String) {
                val numeric = value.replace("%", "").replace(",", "").toDoubleOrNull()
                if (numeric != null) {
                    c.foreground = when {
                        numeric > 0 -> JBColor.RED
                        numeric < 0 -> JBColor.BLUE
                        else -> JBColor.BLACK
                    }
                }
            }
            return c
        }
    }
}
