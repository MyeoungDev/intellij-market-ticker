package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.watch.WatchlistDataService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.ui.rendener.SearchResultRenderer
import com.intellij.openapi.components.service
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-02
 */
class WatchlistView {

    val panel = JPanel(BorderLayout())
    private val listModel = DefaultListModel<Ticker>()
    private val tickerList = JBList(listModel)

    private val watchlistDataService = service<WatchlistDataService>()

    companion object Constant {
        const val EMPTY_TEXT: String = "관심 종목이 없습니다. 검색을 통해 추가하세요."
    }

    init {
        setupUI()
        setupListeners()
        refreshList()
    }

    private fun setupUI() {
        tickerList.cellRenderer = SearchResultRenderer()
        tickerList.emptyText.text = Constant.EMPTY_TEXT

        panel.add(JBScrollPane(tickerList), BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(5)
    }

    private fun setupListeners() {
        tickerList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val index = tickerList.locationToIndex(e.point)
                    if (index != -1) {
                        tickerList.selectedIndex = index
                        showContextMenu(e, listModel.getElementAt(index))
                    }
                }
            }
        })
    }

    private fun showContextMenu(e: MouseEvent, ticker: Ticker) {
        watchlistDataService.removeTicker(ticker.symbol)
        refreshList()
    }

    fun refreshList() {
        listModel.clear()
        val watchTickerList = watchlistDataService.getWatchlist()
        watchTickerList.forEach { ticker -> listModel.addElement(ticker) }
    }

//
//    // 1. 초기 데이터 세팅 (종목명만 먼저 표시)
//    fun setTickers(tickers: List<Ticker>) {
//        tableModel.rowCount = 0
//        rowIndexMap.clear()
//
//        tickers.forEachIndexed { index, ticker ->
//            tableModel.addRow(arrayOf(ticker.name, "-", "-")) // 가격은 아직 모름
//            rowIndexMap[ticker.symbol] = index
//        }
//    }
//
//    // 2. 가격 업데이트 (실시간 반영)
//    fun updatePrice(price: TickerPrice) {
//        val row = rowIndexMap[price.symbol] ?: return
//
//        // UI 스레드에서 안전하게 업데이트 (이미 EDT 안에서 호출되겠지만 안전장치)
//        tableModel.setValueAt(price.currentPrice, row, 1) // 현재가 컬럼
//        tableModel.setValueAt("${price.changeAmount} (${price.changeRate})", row, 2) // 등락률 컬럼
//    }


}