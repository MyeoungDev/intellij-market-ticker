package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * 관심 종목 View 를 나타내는 클래스
 *
 * @author  : 강명관
 * @since   : 2025-12-02
 */

private val logger = KotlinLogging.logger {}

class WatchlistView {

    val panel = JPanel(BorderLayout())

    private val tableModel = object : DefaultTableModel(arrayOf("종목명", "현재가", "등락률"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private val tickerTable = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = 30
        emptyText.text = "관심 종목이 없습니다. 검색을 통해 추가하세요."
    }

    init {
        setupUI()
    }

    private fun setupUI() {
        tickerTable.setDefaultRenderer(Object::class.java, PriceCellRenderer())

        panel.add(JBScrollPane(tickerTable), BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(5)
    }

    fun updateWith(prices: List<TickerPrice>) {
        SwingUtilities.invokeLater {

            logger.info { "Rendering table with ${prices.size} items" }

            tableModel.rowCount = 0

            if (prices.isEmpty()) {
                return@invokeLater
            }

            if (tableModel.rowCount != prices.size) {
                tableModel.rowCount = 0
                prices.forEach {
                    tableModel.addRow(arrayOf(it.name, "-", "-"))
                }
            }

            prices.forEachIndexed { index, price ->
                val sign = if (price.changeRate > 0) "+" else ""
                val rateText = "$sign${price.changeRate}%"

                if (tableModel.getValueAt(index, 1) != price.currentPrice) {
                    tableModel.setValueAt(price.currentPrice, index, 1)
                    tableModel.setValueAt(rateText, index, 2)
                }

                if (tableModel.getValueAt(index, 0) != price.name) {
                    tableModel.setValueAt(price.name, index, 0)
                }
            }

//            prices.forEach { price ->
//                val sign = when {
//                    price.changeRate > 0 -> "+"
//                    price.changeRate < 0 -> ""
//                    else -> ""
//                }
//
//                val changeText = "$sign${price.changeRate}%"
//                tableModel.addRow(arrayOf(price.name, price.currentPrice, changeText))
//            }
        }
    }

    private class PriceCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

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