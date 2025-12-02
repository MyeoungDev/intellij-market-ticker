package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.search.NaverSearchProvider
import com.github.myeoungdev.marketticker.application.search.SearchProvider
import com.github.myeoungdev.marketticker.application.watch.WatchlistDataService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchItem
import com.github.myeoungdev.marketticker.infrastructure.naver.toTicker
import com.github.myeoungdev.marketticker.ui.rendener.SearchResultRenderer
import com.intellij.openapi.components.service
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


/**
 * Market Ticker Plugin Main UI
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
class MarketTickerView(
    private val searchProvider: SearchProvider = NaverSearchProvider()
) {

    val panel = JPanel(BorderLayout())
    private val searchField = JBTextField()
    private val listModel = DefaultListModel<Ticker>()
    private val resultList = JBList(listModel)

    // 서비스 및 코루틴 스코프
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var searchJob: Job? = null

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // 검색창 설정
        searchField.emptyText.text = "종목명 또는 코드 검색 (예: 삼성전자, NVDA)"
        searchField.margin = JBUI.insets(5)

        // 리스트 설정
        resultList.cellRenderer = SearchResultRenderer()

        // 레이아웃 배치
        panel.add(searchField, BorderLayout.NORTH)
        panel.add(JBScrollPane(resultList), BorderLayout.CENTER)
    }

    private fun setupListeners() {
        // 검색창 입력 리스너 (디바운스 적용)
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onQueryChanged()
            override fun removeUpdate(e: DocumentEvent) = onQueryChanged()
            override fun changedUpdate(e: DocumentEvent) = onQueryChanged()
        })

        resultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = resultList.selectedValue
                    val ticker = selected
                    val service = service<WatchlistDataService>()
                    service.addTicker(ticker)
                    println("관심 종목 추가됨: ${ticker.name}")
                }
            }
        })
    }

    private fun onQueryChanged() {
        val query = searchField.text.trim()

        // 이전 검색 취소
        searchJob?.cancel()

        searchJob = scope.launch {
            delay(300) // 300ms 디바운스 (입력 대기)

            val results = try {
                searchProvider.search(query)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }

            withContext(Dispatchers.Main) {
                listModel.clear()
                results.forEach { o -> listModel.addElement(o) }
            }
        }
    }
}