package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.application.service.MarketIndicatorService
import com.github.myeoungdev.marketticker.application.service.MoneyDisplayFormatter
import com.github.myeoungdev.marketticker.application.service.ScreenerService
import com.github.myeoungdev.marketticker.common.extenion.parseCommaToDouble
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenedTicker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenerPreset
import com.github.myeoungdev.marketticker.domain.model.screener.availableScreenerPresets
import com.github.myeoungdev.marketticker.domain.model.screener.screenerLabelEn
import com.github.myeoungdev.marketticker.domain.model.screener.screenerLabelKo
import com.github.myeoungdev.marketticker.domain.model.screener.screenerMarkets
import com.github.myeoungdev.marketticker.domain.model.screener.screenerPresetLabelEn
import com.github.myeoungdev.marketticker.domain.model.screener.screenerPresetLabelKo
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import java.util.concurrent.atomic.AtomicLong

class ScreenerView : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val marketDataService = service<MarketDataService>()
    private val marketIndicatorService = service<MarketIndicatorService>()
    private val screenerService = service<ScreenerService>()
    private val moneyDisplayFormatter = MoneyDisplayFormatter()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val marketCombo = JComboBox(DefaultComboBoxModel(MarketType.screenerMarkets().toTypedArray()))
    private val presetCombo = JComboBox(DefaultComboBoxModel(ScreenerPreset.values()))
    private val refreshButton = JButton()
    private val statusLabel = JLabel()
    private val screenTableModel = object : DefaultTableModel(
        arrayOf(
            localizationService.text("종목명", "Name"),
            localizationService.text("현재가", "Price"),
            localizationService.text("등락값", "Change Amt"),
            localizationService.text("등락률", "Change %")
        ),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val screenTable = JBTable(screenTableModel).apply {
        rowHeight = 28
        emptyText.text = localizationService.text("스크리너 결과가 없습니다.", "No screener result.")
        setDefaultRenderer(Object::class.java, ScreenerCellRenderer())
    }

    private var currentRows: List<ScreenedTicker> = emptyList()
    private val loadSequence = AtomicLong()

    init {
        border = JBUI.Borders.empty(10)
        add(buildToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(screenTable), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
        setupUiText()
        bindTable()
        subscribeSettingsUpdates()
        subscribeMarketIndicatorUpdates()
        loadPreset(forceRefresh = false)
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun buildToolbar(): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            add(
                JPanel(java.awt.GridLayout(1, 2, 8, 0)).apply {
                    add(marketCombo)
                    add(presetCombo)
                },
                BorderLayout.CENTER
            )
            add(refreshButton, BorderLayout.EAST)
        }.also {
            marketCombo.addActionListener {
                rebuildPresetModel()
                loadPreset(forceRefresh = false)
            }
            presetCombo.addActionListener { loadPreset(forceRefresh = false) }
            refreshButton.addActionListener { loadPreset(forceRefresh = true) }
        }
    }

    private fun setupUiText() {
        refreshButton.text = localizationService.text("새로고침", "Refresh")
        statusLabel.text = localizationService.text("스크리너를 불러오는 중...", "Loading screener...")
        marketCombo.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val market = value as? MarketType
                label.text = market?.let { localizationService.text(it.screenerLabelKo(), it.screenerLabelEn()) } ?: ""
                return label
            }
        }
        presetCombo.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val preset = value as? ScreenerPreset
                label.text = preset?.let { displayPreset(currentMarket(), it) } ?: ""
                return label
            }
        }
        marketCombo.selectedItem = MarketType.KOREA
        rebuildPresetModel()

        screenTable.columnModel.getColumn(0).preferredWidth = 145
        screenTable.columnModel.getColumn(1).preferredWidth = 90
        screenTable.columnModel.getColumn(2).preferredWidth = 90
        screenTable.columnModel.getColumn(3).preferredWidth = 70
    }

    private fun bindTable() {
        val popupMenu = JPopupMenu()
        val addItem = JMenuItem(localizationService.text("관심종목 추가", "Add to watchlist"))
        addItem.addActionListener {
            val row = screenTable.selectedRow.takeIf { it >= 0 } ?: return@addActionListener
            resolveAndAdd(currentRows.getOrNull(row)?.ticker ?: return@addActionListener)
        }
        popupMenu.add(addItem)
        screenTable.componentPopupMenu = popupMenu

        screenTable.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val row = screenTable.rowAtPoint(e.point)
                    if (row >= 0) {
                        resolveAndAdd(currentRows.getOrNull(row)?.ticker ?: return)
                    }
                }
            }
        })
    }

    private fun resolveAndAdd(ticker: Ticker) {
        scope.launch {
            val resolved = marketDataService.search(ticker.symbol)
                .firstOrNull { it.symbol.equals(ticker.symbol, ignoreCase = true) }
                ?: ticker
            withContext(Dispatchers.Main) {
                marketDataService.addTicker(resolved)
                statusLabel.text = localizationService.text(
                    "${resolved.name} 을(를) 관심종목에 추가했습니다.",
                    "Added ${resolved.name} to watchlist."
                )
            }
        }
    }

    private fun loadPreset(forceRefresh: Boolean) {
        val market = currentMarket()
        val preset = presetCombo.selectedItem as? ScreenerPreset ?: availablePresetsFor(market).first()
        val requestId = loadSequence.incrementAndGet()
        statusLabel.text = localizationService.text("스크리너를 불러오는 중...", "Loading screener...")

        scope.launch {
            val result = runCatching {
                screenerService.loadScreen(market, preset, limit = 25, forceRefresh = forceRefresh)
            }
            withContext(Dispatchers.Main) {
                if (requestId != loadSequence.get()) {
                    return@withContext
                }
                val rows = result.getOrElse {
                    currentRows = emptyList()
                    renderRows(emptyList())
                    statusLabel.text = localizationService.text(
                        "${displayMarket(market)} · ${displayPreset(market, preset)} 조회 실패",
                        "${displayMarket(market)} · ${displayPreset(market, preset)} failed"
                    )
                    return@withContext
                }
                currentRows = rows
                renderRows(rows)
                statusLabel.text = localizationService.text(
                    "${displayMarket(market)} · ${displayPreset(market, preset)} ${rows.size}건",
                    "${displayMarket(market)} · ${displayPreset(market, preset)} ${rows.size} items"
                )
            }
        }
    }

    private fun renderRows(rows: List<ScreenedTicker>) {
        screenTableModel.rowCount = 0
        rows.forEach { row ->
            screenTableModel.addRow(
                arrayOf(
                    row.ticker.name,
                    formatPriceText(row),
                    formatChangeAmountText(row.changeAmount, row),
                    formatChangeRateText(row.changeRate)
                )
            )
        }
        screenTable.repaint()
    }

    private fun rebuildPresetModel() {
        val market = currentMarket()
        val presets = availablePresetsFor(market)
        val currentSelection = presetCombo.selectedItem as? ScreenerPreset
        val model = DefaultComboBoxModel(presets.toTypedArray())
        presetCombo.model = model
        presetCombo.selectedItem = currentSelection?.takeIf { presets.contains(it) } ?: presets.first()
    }

    private fun currentMarket(): MarketType {
        return marketCombo.selectedItem as? MarketType ?: MarketType.KOREA
    }

    private fun subscribeSettingsUpdates() {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(SettingsUpdateListener.TOPIC, object : SettingsUpdateListener {
                override fun onSettingsUpdated() {
                    ApplicationManager.getApplication().invokeLater {
                        renderRows(currentRows)
                    }
                }
            })
    }

    private fun subscribeMarketIndicatorUpdates() {
        scope.launch {
            marketIndicatorService.indicators.collect {
                withContext(Dispatchers.Main) {
                    renderRows(currentRows)
                }
            }
        }
    }

    private fun availablePresetsFor(market: MarketType): List<ScreenerPreset> {
        return market.availableScreenerPresets()
    }

    private fun displayMarket(market: MarketType): String {
        return localizationService.text(market.screenerLabelKo(), market.screenerLabelEn())
    }

    private fun displayPreset(market: MarketType, preset: ScreenerPreset): String {
        return localizationService.text(
            market.screenerPresetLabelKo(preset),
            market.screenerPresetLabelEn(preset)
        )
    }

    private fun formatChangeRateText(raw: String): String {
        val value = raw.replace("%", "").replace(",", "").trim().toDoubleOrNull() ?: return raw
        val sign = if (value > 0) "+" else ""
        return "$sign${localizationService.formatDecimal(value, 2)}%"
    }

    private fun formatChangeAmountText(raw: String, row: ScreenedTicker): String {
        val value = raw.replace(",", "").trim().toDoubleOrNull() ?: return raw
        val sign = when {
            value > 0 -> "+"
            value < 0 -> "-"
            else -> ""
        }
        val digits = when (row.ticker.marketType.nativeCurrency()) {
            CurrencyType.KRW -> 0
            else -> 2
        }
        return "$sign${localizationService.formatDecimal(kotlin.math.abs(value), digits)}"
    }

    private fun formatPriceText(row: ScreenedTicker): String {
        val rawPrice = row.price.parseCommaToDouble()
        if (rawPrice <= 0.0) {
            return row.price
        }

        val currency = row.ticker.marketType.nativeCurrency().takeIf { it != CurrencyType.UNKNOWN }
            ?: currentMarket().nativeCurrency()
        val fractionDigits = when (currency) {
            CurrencyType.KRW -> 0
            else -> 2
        }

        return moneyDisplayFormatter.formatAmount(rawPrice, currency, fractionDigits)
    }

    private class ScreenerCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (table == null) {
                return component
            }

            component.foreground = if (isSelected) table.selectionForeground else table.foreground

            if (column in setOf(2, 3) && value is String) {
                val numeric = value.parseCommaToDouble()
                component.foreground = when {
                    numeric > 0 -> JBColor.RED
                    numeric < 0 -> JBColor.BLUE
                    else -> if (isSelected) table.selectionForeground else table.foreground
                }
            }

            return component
        }
    }
}
