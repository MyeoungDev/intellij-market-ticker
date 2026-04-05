package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.application.service.ScreenerService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenedTicker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenerPreset
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
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
import javax.swing.table.DefaultTableModel

class ScreenerView : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val marketDataService = service<MarketDataService>()
    private val screenerService = service<ScreenerService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val presetCombo = JComboBox(DefaultComboBoxModel(ScreenerPreset.values()))
    private val refreshButton = JButton()
    private val statusLabel = JLabel()
    private val screenTableModel = object : DefaultTableModel(
        arrayOf("Ticker", "Company", "Price", "Change", "Volume", "Market Cap", "PER"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val screenTable = JBTable(screenTableModel).apply {
        rowHeight = 28
        emptyText.text = localizationService.text("스크리너 결과가 없습니다.", "No screener result.")
    }

    private var currentRows: List<ScreenedTicker> = emptyList()

    init {
        border = JBUI.Borders.empty(10)
        add(buildToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(screenTable), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
        setupUiText()
        bindTable()
        loadPreset(forceRefresh = false)
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun buildToolbar(): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            add(presetCombo, BorderLayout.CENTER)
            add(refreshButton, BorderLayout.EAST)
        }.also {
            presetCombo.addActionListener { loadPreset(forceRefresh = false) }
            refreshButton.addActionListener { loadPreset(forceRefresh = true) }
        }
    }

    private fun setupUiText() {
        refreshButton.text = localizationService.text("새로고침", "Refresh")
        statusLabel.text = localizationService.text("스크리너를 불러오는 중...", "Loading screener...")
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
                label.text = preset?.let { displayPreset(it) } ?: ""
                return label
            }
        }
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
        val preset = presetCombo.selectedItem as? ScreenerPreset ?: ScreenerPreset.SEARCH_TOP
        statusLabel.text = localizationService.text("스크리너를 불러오는 중...", "Loading screener...")

        scope.launch {
            val rows = screenerService.loadScreen(preset, limit = 25, forceRefresh = forceRefresh)
            withContext(Dispatchers.Main) {
                currentRows = rows
                screenTableModel.rowCount = 0
                rows.forEach { row ->
                    screenTableModel.addRow(
                        arrayOf(
                            row.ticker.symbol,
                            row.ticker.name,
                            row.price,
                            row.change,
                            row.volume,
                            row.marketCap,
                            row.pe
                        )
                    )
                }
                statusLabel.text = localizationService.text(
                    "${displayPreset(preset)} ${rows.size}건",
                    "${displayPreset(preset)} ${rows.size} items"
                )
            }
        }
    }

    private fun displayPreset(preset: ScreenerPreset): String {
        return localizationService.text(preset.labelKo, preset.labelEn)
    }
}
