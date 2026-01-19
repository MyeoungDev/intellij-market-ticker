package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.ui.rendener.SearchResultRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import io.github.oshai.kotlinlogging.KotlinLogging
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

private val logger = KotlinLogging.logger {}

class MarketTickerView(
    private val project: Project
) : Disposable {

    private val marketDataService = service<MarketDataService>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var searchJob: Job = Job()

    val searchPanel = JPanel(BorderLayout())
    private val searchField = JBTextField()
    private val searchListModel = DefaultListModel<Ticker>()
    private val searchResultList = JBList(searchListModel)

    private val watchlistView = WatchlistView(project)


    override fun dispose() {
        scope.cancel()
    }

    init {
        setupUI()
        setupListeners()
        observeWatchlistChanges()
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

        searchPanel.add(searchField, BorderLayout.NORTH)
        searchPanel.add(splitter, BorderLayout.CENTER)

    }

    private fun setupListeners() {
        // 검색창 입력 리스너
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onQueryChanged()
            override fun removeUpdate(e: DocumentEvent) = onQueryChanged()
            override fun changedUpdate(e: DocumentEvent) = onQueryChanged()
        })

        searchResultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val ticker = searchResultList.selectedValue ?: return
                    marketDataService.addTicker(ticker)

                    searchField.text = ""
                    searchListModel.clear()
                    logger.info { "관심 종목 추가 요청: ${ticker.name}" }
                }
            }
        })
    }

    private fun onQueryChanged() {
        val query = searchField.text.trim()

        searchJob?.cancel()

        searchJob = scope.launch {
            delay(300) // 300ms 디바운스 (입력 대기)

            val tickerList = marketDataService.search(query)

            withContext(Dispatchers.Main) {
                searchListModel.clear()
                tickerList.forEach { ticker -> searchListModel.addElement(ticker) }
            }
        }
    }

    private fun observeWatchlistChanges() {
        scope.launch {
            marketDataService.currentPrices.collect { prices ->
                logger.info { "View received prices: ${prices.size}" }

                withContext(Dispatchers.Main) {
                    logger.info { "Updating WatchlistView on Main Thread" }
                    watchlistView.updateWith(prices)
                }
            }
        }
    }

}