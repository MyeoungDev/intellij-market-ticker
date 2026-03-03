package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.WatchlistEntryUpdateListener
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class PortfolioView {

    private val marketDataService = service<MarketDataService>()
    private val localizationService = service<LocalizationService>()

    val panel = JPanel(BorderLayout())
    private val tableModel =
        object : DefaultTableModel(
            arrayOf(
                localizationService.text("종목명", "Symbol"),
                localizationService.text("현재가", "Price"),
                localizationService.text("수량", "Qty"),
                localizationService.text("매수평균", "Avg Buy"),
                localizationService.text("평가금액", "Market Value"),
                localizationService.text("실현손익", "Realized"),
                localizationService.text("미실현손익", "Unrealized"),
                localizationService.text("총손익", "Total PnL"),
                localizationService.text("비중", "Weight"),
                localizationService.text("목표비중", "Target"),
                localizationService.text("편차", "Deviation")
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

        editPortfolioItem.addActionListener {
            val selectedRow = portfolioTable.selectedRow
            if (selectedRow != -1) {
                getWatchlistEntryAtRow(selectedRow)?.let { entry ->
                    val dialog = PortfolioEditDialog(entry)
                    if (dialog.showAndGet()) {
                        val updatedEntry = entry.copy(
                            purchasePrice = dialog.purchasePrice,
                            quantity = dialog.quantity,
                            targetWeightPercentage = dialog.targetWeightPercentage,
                            realizedProfitLoss = dialog.realizedProfitLoss,
                            groupTag = dialog.groupTag
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
                    val realized = entry.realizedProfitLoss ?: 0.0

                    Triple(entry, price, PortfolioMetrics(marketValue, realized, unrealized))
                }

            val totalMarketValue = portfolioRows.sumOf { it.third.marketValue }

            portfolioRows.forEach { (entry, price, metrics) ->
                val totalPnL = metrics.realized + metrics.unrealized
                val actualWeight = if (totalMarketValue > 0.0) (metrics.marketValue / totalMarketValue) * 100 else 0.0
                val targetWeight = entry.targetWeightPercentage ?: 0.0
                val deviation = actualWeight - targetWeight

                tableModel.addRow(
                    arrayOf(
                        entry.name,
                        localizationService.formatDecimal(price?.currentPrice ?: 0.0, 2),
                        localizationService.formatDecimal(entry.quantity ?: 0.0, 2),
                        localizationService.formatDecimal(entry.purchasePrice ?: 0.0, 2),
                        localizationService.formatDecimal(metrics.marketValue, 2),
                        localizationService.formatDecimal(metrics.realized, 2),
                        localizationService.formatDecimal(metrics.unrealized, 2),
                        localizationService.formatDecimal(totalPnL, 2),
                        localizationService.formatPercent(actualWeight),
                        localizationService.formatPercent(targetWeight),
                        localizationService.formatPercent(deviation)
                    )
                )
            }

            portfolioTable.repaint()
        }
    }

    private fun getWatchlistEntryAtRow(row: Int): WatchlistRepository.WatchlistEntry? {
        val filteredEntries = currentWatchlistEntries.filter { entry ->
            val quantity = entry.quantity ?: 0.0
            val purchasePrice = entry.purchasePrice ?: 0.0
            quantity > 0 && purchasePrice > 0
        }
        return filteredEntries.getOrNull(row)
    }

    private data class PortfolioMetrics(
        val marketValue: Double,
        val realized: Double,
        val unrealized: Double
    )

    private class PriceCellRenderer : DefaultTableCellRenderer() {
        private val signedColumns = setOf(5, 6, 7, 10)

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
