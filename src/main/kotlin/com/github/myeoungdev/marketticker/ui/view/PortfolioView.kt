package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.WatchlistEntryUpdateListener
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

private val logger = KotlinLogging.logger {}

class PortfolioView {

    private val marketDataService = service<MarketDataService>()

    val panel = JPanel(BorderLayout())
    private val tableModel =
        object : DefaultTableModel(
            arrayOf("종목명", "현재가", "등락률", "수량", "매수평균", "평가금액", "평가손익", "수익률"),
            0
        ) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
    private val portfolioTable = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = 30
        emptyText.text = "포트폴리오에 종목이 없습니다. 관심종목에서 포트폴리오 정보를 추가하세요."
    }

    private var currentWatchlistEntries: List<WatchlistRepository.WatchlistEntry> = emptyList()
    private var currentPrices: List<TickerPrice> = emptyList()

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
        val editPortfolioItem = JMenuItem("포트폴리오 정보 편집")

        editPortfolioItem.addActionListener {
            val selectedRow = portfolioTable.selectedRow
            if (selectedRow != -1) {
                getWatchlistEntryAtRow(selectedRow)?.let { entry ->
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
        popupMenu.add(editPortfolioItem)
        portfolioTable.componentPopupMenu = popupMenu
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

            currentWatchlistEntries
                .forEach { entry ->
                    entry.quantity?.let { quantity ->
                        entry.purchasePrice?.let { purchasePrice ->
                            if (quantity > 0 && purchasePrice > 0) {
                                val price = currentPrices.find {
                                    it.symbol == entry.symbol && it.marketType == MarketType.valueOf(entry.marketType)
                                }

                                val currentPrice = price?.currentPrice
                                val changeRate = price?.changeRate ?: 0.0

                                val evaluationAmount = if (currentPrice != null) quantity * currentPrice else 0.0
                                val profitLoss =
                                    if (currentPrice != null) (currentPrice - purchasePrice) * quantity else 0.0
                                val returnRate =
                                    if (purchasePrice > 0) (profitLoss / (purchasePrice * quantity)) * 100 else 0.0

                                val sign = if (changeRate > 0) "+" else ""
                                val rateText = "$sign${String.format("%.2f", changeRate)}%"

                                tableModel.addRow(
                                    arrayOf(
                                        entry.name,
                                        currentPrice?.let { String.format("%,.2f", it) } ?: "-",
                                        rateText,
                                        quantity.toInt(),
                                        String.format("%,.2f", purchasePrice),
                                        String.format("%,.2f", evaluationAmount),
                                        String.format("%,.2f", profitLoss),
                                        String.format("%,.2f%%", returnRate)
                                    )
                                )
                            }
                        }
                    }
                }
            portfolioTable.repaint()
        }
    }

    private fun getWatchlistEntryAtRow(row: Int): WatchlistRepository.WatchlistEntry? {
        val filteredEntries = currentWatchlistEntries.filter { entry ->
            entry.quantity?.let { quantity ->
                entry.purchasePrice?.let { purchasePrice ->
                    quantity > 0 && purchasePrice > 0
                } ?: false
            } ?: false
        }
        return filteredEntries.getOrNull(row)
    }

    private class PriceCellRenderer : DefaultTableCellRenderer() {
        companion object {
            private const val CHANGE_RATE_COLUMN_INDEX = 2 // 등락률
            private const val EVALUATION_AMOUNT_COLUMN_INDEX = 5 // 평가금액
            private const val PROFIT_LOSS_COLUMN_INDEX = 6 // 평가손익
            private const val RETURN_RATE_COLUMN_INDEX = 7 // 수익률
        }

        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

            if (table == null) return c

            // Reset foreground to default first
            c.foreground = if (isSelected) table.selectionForeground else table.foreground

            when (column) {
                CHANGE_RATE_COLUMN_INDEX -> { // 등락률
                    if (value is String) {
                        c.foreground = when {
                            value.startsWith("+") -> JBColor.RED
                            value.startsWith("-") -> JBColor.BLUE
                            else -> JBColor.BLACK
                        }
                    }
                }
                PROFIT_LOSS_COLUMN_INDEX, RETURN_RATE_COLUMN_INDEX -> { // 평가손익, 수익률
                    if (value is String) {
                        // Attempt to parse string to double for comparison
                        val numericValue = value.replace(",", "").replace("%", "").toDoubleOrNull()
                        if (numericValue != null) {
                            c.foreground = when {
                                numericValue > 0 -> JBColor.RED
                                numericValue < 0 -> JBColor.BLUE
                                else -> JBColor.BLACK
                            }
                        }
                    }
                }
                EVALUATION_AMOUNT_COLUMN_INDEX -> { // 평가금액 (평가손익에 따라 색상 결정)
                    val profitLossValue = table.getValueAt(row, PROFIT_LOSS_COLUMN_INDEX)
                    if (profitLossValue is String) {
                        val numericProfitLoss = profitLossValue.replace(",", "").toDoubleOrNull()
                        if (numericProfitLoss != null) {
                            c.foreground = when {
                                numericProfitLoss > 0 -> JBColor.RED
                                numericProfitLoss < 0 -> JBColor.BLUE
                                else -> JBColor.BLACK
                            }
                        }
                    }
                }
            }
            return c
        }
    }
}
