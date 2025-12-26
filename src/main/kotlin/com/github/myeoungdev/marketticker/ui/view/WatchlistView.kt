package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.TickerUpdateListener
import com.github.myeoungdev.marketticker.application.manager.MarketTickerManager
import com.github.myeoungdev.marketticker.application.service.PriceAlertService
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
import java.util.*
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

class WatchlistView(private val project: Project) {

    private val marketTickerManager = service<MarketTickerManager>()
    private val alertService = service<PriceAlertService>()

    private var currentPrices: List<TickerPrice> = emptyList()

    val panel = JPanel(BorderLayout())

    private val tableModel = object : DefaultTableModel(arrayOf("종목명", "현재가", "등락률", "알람"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private val tickerTable = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = 30
        emptyText.text = "관심 종목이 없습니다. 검색을 통해 추가하세요."
    }

    init {
        setupUI()
        subscribeToTickerUpdates()
        loadInitialData()
    }

    private fun loadInitialData() {
        val currentData = marketTickerManager.currentPrices.value

        if (currentData.isNotEmpty()) {
            logger.info { "Loading initial data from cache: ${currentData.size}" }
            updateTable(currentData)
        } else {
            logger.info { "Cache empty. Requesting force refresh..." }
            marketTickerManager.forceRefresh()
        }
    }

    private fun setupUI() {
        // 테이블 설정
        tickerTable.setDefaultRenderer(Object::class.java, PriceCellRenderer())

        // 알람 아이콘 컬럼 설정 (3번째 인덱스)
        tickerTable.columnModel.getColumn(3).apply {
            maxWidth = 40
            minWidth = 40
            cellRenderer = AlertIconRenderer()
        }

        // 클릭 이벤트 리스너 (알람 설정 다이얼로그)
        tickerTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return

                val row = tickerTable.rowAtPoint(e.point)
                if (row < 0) return

                val ticker = getTickerAtRow(row)
                if (ticker != null) {
                    // [New] 다이얼로그 호출 및 닫힌 후 UI 갱신
                    if (AlertSettingsDialog(ticker).showAndGet()) {
                        // 저장 후 즉시 리페인트하여 아이콘 상태 변경 반영
                        tickerTable.repaint()
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
                    updateTable(prices)
                }
            }
        })
    }

    private fun updateTable(prices: List<TickerPrice>) {
        // currentPrices 업데이트는 즉시 수행 (렌더러 참조용)
        currentPrices = prices

        // UI 갱신은 반드시 EDT에서 수행
        ApplicationManager.getApplication().invokeLater {
            if (tableModel.rowCount != prices.size) {
                // 개수가 다르면 전체 재생성 (초기화 또는 목록 변경 시)
                tableModel.rowCount = 0
                prices.forEach {
                    tableModel.addRow(arrayOf(it.name, "-", "-", ""))
                }
            }

            // 값 업데이트
            prices.forEachIndexed { index, price ->
                val sign = if (price.changeRate > 0) "+" else ""
                val rateText = "$sign${price.changeRate}%"

                // 값이 변경된 경우에만 setValueAt 호출 (불필요한 리페인트 방지)
                if (tableModel.getValueAt(index, 0) != price.name)
                    tableModel.setValueAt(price.name, index, 0)

                if (tableModel.getValueAt(index, 1) != price.currentPrice)
                    tableModel.setValueAt(price.currentPrice, index, 1)

                if (tableModel.getValueAt(index, 2) != rateText)
                    tableModel.setValueAt(rateText, index, 2)
            }

            // 렌더러가 알람 상태 등도 다시 그리도록 트리거
            tickerTable.repaint()
        }
    }

    fun updateWith(prices: List<TickerPrice>) {
        currentPrices = prices

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
        }
    }

    private fun getTickerAtRow(row: Int): Ticker? {
        val tickerPrice = currentPrices.getOrNull(row)

        if (tickerPrice != null) {
            return Ticker(
                symbol = tickerPrice.symbol,
                tradingSymbol = tickerPrice.tradingSymbol,
                name = tickerPrice.name,
                marketType = tickerPrice.marketType,
                nationCode = tickerPrice.nationCode,
                nationName = tickerPrice.nationName
            )
        }

        return null
    }

    inner class AlertIconRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)

            val ticker = getTickerAtRow(row)
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