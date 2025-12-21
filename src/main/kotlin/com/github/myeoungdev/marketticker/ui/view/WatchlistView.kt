package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.watch.WatchlistDataService
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
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
class WatchlistView {
    val panel = JPanel(BorderLayout())

    // 테이블 모델 설정 (수정 불가)
    private val tableModel = object : DefaultTableModel(arrayOf("종목명", "현재가", "등락률"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private val tickerTable = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = 30
        emptyText.text = "관심 종목이 없습니다. 검색을 통해 추가하세요."
    }

    private val watchlistDataService = service<WatchlistDataService>()

    // 종목 코드로 행(Row) 위치를 찾기 위한 매핑
    private val rowIndexMap = mutableMapOf<String, Int>()

    init {
        setupUI()
        refreshList()
    }

    private fun setupUI() {
        // 커스텀 렌더러 적용 (색상 처리)
        tickerTable.setDefaultRenderer(Object::class.java, PriceCellRenderer())

        panel.add(JBScrollPane(tickerTable), BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(5)
    }

    fun refreshList() {
        tableModel.rowCount = 0
        rowIndexMap.clear()

        val tickers = watchlistDataService.getWatchlist()
        tickers.forEachIndexed { index, ticker ->
            // 초기값은 로딩 중(-)
            tableModel.addRow(arrayOf(ticker.name, "-", "-"))
            rowIndexMap[ticker.symbol] = index
        }
    }

    // 실시간 가격 업데이트 메서드
    fun updatePrice(price: TickerPrice) {
        val row = rowIndexMap[price.symbol] ?: return

        SwingUtilities.invokeLater {
            if (row < tableModel.rowCount) {
                tableModel.setValueAt(price.currentPrice, row, 1)

                val sign = if (price.changeRate > 0) "+" else ""
                val percentText = "$sign${price.changeRate}%"
                tableModel.setValueAt(percentText, row, 2)
            }
        }
    }

    // 내부 클래스: 색상 렌더러
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