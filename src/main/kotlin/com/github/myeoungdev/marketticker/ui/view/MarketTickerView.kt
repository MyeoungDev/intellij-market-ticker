package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.search.NaverSearchProvider
import com.github.myeoungdev.marketticker.application.search.SearchProvider
import com.github.myeoungdev.marketticker.application.watch.WatchlistDataService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.ui.rendener.SearchResultRenderer
import com.intellij.openapi.Disposable
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
) : Disposable {

    val searchPanel = JPanel(BorderLayout())
    private val searchField = JBTextField()
    private val searchListModel = DefaultListModel<Ticker>()
    private val searchResultList = JBList(searchListModel)

    private val watchlistView = WatchlistView()

    // 서비스 및 코루틴 스코프
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var searchJob: Job? = null

    override fun dispose() {
        scope.cancel()
    }

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // 검색창 설정
        searchField.emptyText.text = "종목명 또는 코드 검색 (예: 삼성전자, NVDA)"
        searchField.margin = JBUI.insets(5)

        // 리스트 설정
        searchResultList.cellRenderer = SearchResultRenderer()

        // 레이아웃 배치
        searchPanel.add(searchField, BorderLayout.NORTH)

        // 수직 분할, 비율 4:6
        val splitter = com.intellij.ui.JBSplitter(true, 0.4f)

        splitter.firstComponent = JBScrollPane(searchResultList).apply {
            border = javax.swing.BorderFactory.createTitledBorder("검색 결과")
        }

        splitter.secondComponent = watchlistView.panel.apply {
            border = javax.swing.BorderFactory.createTitledBorder("관심 종목")
        }

        // SearchBar 상단 고정
        searchPanel.add(searchField, BorderLayout.NORTH)
        searchPanel.add(splitter, BorderLayout.CENTER)

    }

    private fun setupListeners() {
        // 검색창 입력 리스너 (디바운스 적용)
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onQueryChanged()
            override fun removeUpdate(e: DocumentEvent) = onQueryChanged()
            override fun changedUpdate(e: DocumentEvent) = onQueryChanged()
        })

        searchResultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = searchResultList.selectedValue
                    val ticker = selected
                    val service = service<WatchlistDataService>()
                    service.addTicker(ticker)

                    watchlistView.refreshList()

                    searchField.text = ""
                    searchListModel.clear()

                    println("관심 종목 추가됨: ${ticker.name}")
                }
            }
        })
    }

    private fun onQueryChanged() {
        val query = searchField.text.trim()

        searchJob?.cancel()

        searchJob = scope.launch {
            delay(300) // 300ms 디바운스 (입력 대기)

            val tickerList = try {
                searchProvider.search(query)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }

            withContext(Dispatchers.Main) {
                searchListModel.clear()
                tickerList.forEach { ticker -> searchListModel.addElement(ticker) }
            }
        }
    }
}