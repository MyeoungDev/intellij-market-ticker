package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.listener.WatchlistEntryUpdateListener
import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.application.service.MoneyDisplayFormatter
import com.github.myeoungdev.marketticker.application.service.PortfolioConvertedSummary
import com.github.myeoungdev.marketticker.application.service.PortfolioPosition
import com.github.myeoungdev.marketticker.application.service.PortfolioSummaryCalculator
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ScrollPaneConstants
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class PortfolioView : Disposable {

    private val marketDataService = service<MarketDataService>()
    private val appSettingsService = service<AppSettingsService>()
    private val localizationService = service<LocalizationService>()
    private val moneyDisplayFormatter = MoneyDisplayFormatter()

    val panel = JPanel(BorderLayout())
    private val summaryCompactLabel = JLabel()
    private val summaryToggleButton = JButton("▼").apply {
        border = JBUI.Borders.empty(0, 8)
        isContentAreaFilled = false
        isFocusPainted = false
        isBorderPainted = false
        toolTipText = localizationService.text("요약 상세 보기", "Show summary details")
        addActionListener {
            summaryExpanded = !summaryExpanded
            renderSummaryPanel()
        }
    }
    private val summaryDetailsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.emptyTop(8)
    }
    private val summaryPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(8)
    }
    private val summaryScrollPane = JBScrollPane(summaryPanel).apply {
        border = JBUI.Borders.empty(0, 0, 6, 0)
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    }
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
    private var portfolioSummary: PortfolioConvertedSummary? = null
    private var summaryExpanded = false
    private val messageBusConnections = mutableListOf<MessageBusConnection>()

    var onTickerSelected: ((Ticker, TickerPrice?) -> Unit)? = null

    init {
        setupUI()
        loadInitialData()
        subscribeToWatchlistEntryUpdates()
        subscribeToSettingsUpdates()
    }

    override fun dispose() {
        messageBusConnections.forEach { it.disconnect() }
        messageBusConnections.clear()
    }

    private fun subscribeToWatchlistEntryUpdates() {
        val connection = ApplicationManager.getApplication().messageBus.connect()
        messageBusConnections.add(connection)
        connection.subscribe(WatchlistEntryUpdateListener.TOPIC, object : WatchlistEntryUpdateListener {
            override fun onWatchlistEntryUpdated() {
                ApplicationManager.getApplication().invokeLater {
                    currentWatchlistEntries = marketDataService.getWatchlistEntries()
                    updateTable()
                }
            }
        })
    }

    private fun subscribeToSettingsUpdates() {
        val connection = ApplicationManager.getApplication().messageBus.connect()
        messageBusConnections.add(connection)
        connection.subscribe(SettingsUpdateListener.TOPIC, object : SettingsUpdateListener {
            override fun onSettingsUpdated() {
                ApplicationManager.getApplication().invokeLater {
                    updateTable()
                }
            }
        })
    }

    private fun setupUI() {
        portfolioTable.setDefaultRenderer(Object::class.java, PriceCellRenderer())
        setupSummaryPanel()
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
        panel.add(summaryScrollPane, BorderLayout.NORTH)
        panel.add(JBScrollPane(portfolioTable), BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(5)
        renderSummaryPanel()
    }

    private fun setupSummaryPanel() {
        val summaryTextPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(summaryCompactLabel)
        }
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(summaryTextPanel, BorderLayout.CENTER)
            add(summaryToggleButton, BorderLayout.EAST)
        }
        summaryPanel.add(headerPanel, BorderLayout.NORTH)
        summaryPanel.add(summaryDetailsPanel, BorderLayout.CENTER)
    }

    private fun loadInitialData() {
        currentWatchlistEntries = marketDataService.getWatchlistEntries()
        updateTable()
    }

    fun updateWith(prices: List<TickerPrice>) {
        currentPrices = prices
        currentWatchlistEntries = marketDataService.getWatchlistEntries()
        updateTable()
    }

    private fun updateTable() {
        ApplicationManager.getApplication().invokeLater {
            tableModel.rowCount = 0

            val portfolioRows = PortfolioSummaryCalculator.projectPositions(currentWatchlistEntries, currentPrices)
            portfolioEntries = portfolioRows.map { it.entry }
            portfolioSummary = PortfolioSummaryCalculator.calculateConvertedSummary(
                positions = portfolioRows,
                baseCurrency = appSettingsService.getBaseCurrency(),
                convertAmount = { value, currency, baseCurrency ->
                    moneyDisplayFormatter.convertToBaseCurrency(value, currency, baseCurrency)
                }
            )
            renderSummaryPanel()

            portfolioRows.forEach { position ->
                tableModel.addRow(portfolioRow(position))
            }

            portfolioTable.repaint()
        }
    }

    private fun portfolioRow(position: PortfolioPosition): Array<Any> {
        val returnRate = if (position.purchaseAmount > 0.0) {
            position.unrealized / position.purchaseAmount * 100.0
        } else {
            0.0
        }

        return arrayOf(
            position.entry.name,
            localizationService.formatDecimal(position.quantity, 2),
            moneyDisplayFormatter.formatAmount(position.purchasePrice, position.currency),
            moneyDisplayFormatter.formatAmount(position.price?.currentPrice, position.price?.currency),
            moneyDisplayFormatter.formatAmount(position.marketValue, position.currency),
            SignedDisplayValue(
                text = moneyDisplayFormatter.formatSignedAmount(position.unrealized, position.currency),
                numericValue = position.unrealized
            ),
            SignedDisplayValue(
                text = formatSignedPercent(returnRate),
                numericValue = returnRate
            )
        )
    }

    private fun renderSummaryPanel() {
        summaryDetailsPanel.removeAll()
        val summary = portfolioSummary
        val shouldShowSummary = appSettingsService.isPortfolioSummaryVisible() && summary != null && summary.holdingCount > 0
        summaryScrollPane.isVisible = shouldShowSummary
        if (!shouldShowSummary) {
            summaryScrollPane.revalidate()
            summaryScrollPane.repaint()
            return
        }

        val dailyChangeAmount = summary.dailyChangeAmount
        val summaryColor = signedColor(dailyChangeAmount) ?: summaryPanel.foreground
        summaryCompactLabel.text = formatCompactSummary(summary)
        summaryCompactLabel.toolTipText = formatFullSummary(summary)
        summaryCompactLabel.foreground = summaryColor
        summaryToggleButton.text = if (summaryExpanded) "▲" else "▼"
        summaryToggleButton.toolTipText = if (summaryExpanded) {
            localizationService.text("요약 접기", "Collapse summary")
        } else {
            localizationService.text("요약 상세 보기", "Show summary details")
        }

        summaryDetailsPanel.isVisible = summaryExpanded
        if (summaryExpanded) {
            addSummaryDetailRow(localizationService.text("총 수익금", "Total PnL"), formatSignedAmount(summary.totalProfit, summary.currency), summary.totalProfit)
            addSummaryDetailRow(localizationService.text("총 수익률", "Total return"), formatSignedPercent(summary.totalReturnRate), summary.totalReturnRate)
            addSummaryDetailRow(localizationService.text("총 매입금액", "Purchase amount"), moneyDisplayFormatter.formatNativeAmount(summary.totalPurchaseAmount, summary.currency), null)
        }
        if (summary.excludedHoldingCount > 0) {
            addSummaryDetailRow(
                localizationService.text("환산 제외", "Excluded from conversion"),
                formatHoldingCount(summary.excludedHoldingCount),
                null
            )
        }
        summaryScrollPane.preferredSize = Dimension(0, if (summaryExpanded) 150 else 44)
        summaryScrollPane.revalidate()
        summaryScrollPane.repaint()
        summaryDetailsPanel.revalidate()
        summaryDetailsPanel.repaint()
    }

    private fun addSummaryDetailRow(label: String, value: String, numericValue: Double?) {
        summaryDetailsPanel.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 0)
            add(JLabel(label), BorderLayout.WEST)
            add(JLabel(value).apply {
                horizontalAlignment = SwingConstants.RIGHT
                foreground = signedColor(numericValue) ?: foreground
            }, BorderLayout.EAST)
        })
    }

    private fun formatDailySummary(summary: PortfolioConvertedSummary): String {
        val dailyRate = formatSignedPercent(summary.dailyChangeRate)
        val dailyAmount = formatSignedAmount(summary.dailyChangeAmount, summary.currency)
        return localizationService.text("일일 $dailyRate ($dailyAmount)", "Daily $dailyRate ($dailyAmount)")
    }

    private fun formatCompactSummary(summary: PortfolioConvertedSummary): String {
        return localizationService.text(
            "총 평가 ${formatCompactAmount(summary.totalMarketValue, summary.currency)}  일일 ${formatSignedPercent(summary.dailyChangeRate)} (${formatCompactSignedAmount(summary.dailyChangeAmount, summary.currency, includeCurrency = false)})",
            "Value ${formatCompactAmount(summary.totalMarketValue, summary.currency)}  Daily ${formatSignedPercent(summary.dailyChangeRate)} (${formatCompactSignedAmount(summary.dailyChangeAmount, summary.currency, includeCurrency = false)})"
        )
    }

    private fun formatFullSummary(summary: PortfolioConvertedSummary): String {
        return "${moneyDisplayFormatter.formatNativeAmount(summary.totalMarketValue, summary.currency)} / ${formatDailySummary(summary)}"
    }

    private fun formatCompactSignedAmount(value: Double?, currency: CurrencyType, includeCurrency: Boolean = true): String {
        if (value == null || !value.isFinite()) return "-"
        val formatted = formatCompactAmount(kotlin.math.abs(value), currency, includeCurrency)
        return when {
            value > 0 -> "+$formatted"
            value < 0 -> "-$formatted"
            else -> formatted
        }
    }

    private fun formatCompactAmount(value: Double?, currency: CurrencyType, includeCurrency: Boolean = true): String {
        if (value == null || !value.isFinite()) return "-"
        val text = when (currency) {
            CurrencyType.KRW -> formatKrwCompactAmount(value)
            else -> formatInternationalCompactAmount(value)
        }
        return if (includeCurrency) "$text ${currency.code}" else text
    }

    private fun formatKrwCompactAmount(value: Double): String {
        val absValue = kotlin.math.abs(value)
        return when {
            absValue >= 100_000_000.0 -> "${localizationService.formatDecimal(value / 100_000_000.0, 1)}억"
            absValue >= 10_000.0 -> "${localizationService.formatDecimal(value / 10_000.0, 1)}만"
            else -> localizationService.formatDecimal(value, 0)
        }
    }

    private fun formatInternationalCompactAmount(value: Double): String {
        val absValue = kotlin.math.abs(value)
        return when {
            absValue >= 1_000_000_000.0 -> "${localizationService.formatDecimal(value / 1_000_000_000.0, 1)}B"
            absValue >= 1_000_000.0 -> "${localizationService.formatDecimal(value / 1_000_000.0, 1)}M"
            absValue >= 1_000.0 -> "${localizationService.formatDecimal(value / 1_000.0, 1)}K"
            else -> localizationService.formatDecimal(value, 1)
        }
    }

    private fun formatSignedAmount(value: Double?, currency: CurrencyType): String {
        return moneyDisplayFormatter.formatNativeSignedAmount(value, currency)
    }

    private fun formatSignedPercent(value: Double?): String {
        if (value == null) return "-"
        val formatted = localizationService.formatPercent(kotlin.math.abs(value))
        return when {
            value > 0 -> "+$formatted"
            value < 0 -> "-$formatted"
            else -> formatted
        }
    }

    private fun formatHoldingCount(count: Int): String {
        return localizationService.text("${count}개", "$count")
    }

    private fun signedColor(value: Double?): Color? {
        return when {
            value == null -> null
            value > 0.0 -> JBColor.RED
            value < 0.0 -> JBColor.BLUE
            else -> null
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

    private data class SignedDisplayValue(
        val text: String,
        val numericValue: Double
    )

    private class PriceCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val displayValue = if (value is SignedDisplayValue) value.text else value
            val c = super.getTableCellRendererComponent(table, displayValue, isSelected, hasFocus, row, column)
            if (table == null) return c

            c.foreground = if (isSelected) table.selectionForeground else table.foreground

            if (!isSelected && value is SignedDisplayValue) {
                c.foreground = when {
                    value.numericValue > 0 -> JBColor.RED
                    value.numericValue < 0 -> JBColor.BLUE
                    else -> table.foreground
                }
            }
            return c
        }
    }
}
