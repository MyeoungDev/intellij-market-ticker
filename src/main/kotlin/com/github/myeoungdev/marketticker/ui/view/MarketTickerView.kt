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
import javax.swing.JTabbedPane
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
    private val portfolioView = PortfolioView()

    private val heatmapView = HeatmapView()

    val mainPanel = JPanel(BorderLayout())

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

        val searchResultPanel = JBScrollPane(searchResultList).apply {
            border = javax.swing.BorderFactory.createTitledBorder("검색 결과")
        }

        // 관심종목/포트폴리오 탭
        val watchlistPortfolioTabbedPane = JTabbedPane().apply {
            addTab("관심종목", watchlistView.panel)
            addTab("포트폴리오", portfolioView.panel)
        }

        val topSplitter = com.intellij.ui.JBSplitter(true, 0.4f).apply {
            firstComponent = searchResultPanel
            secondComponent = watchlistPortfolioTabbedPane
        }

        searchPanel.add(searchField, BorderLayout.NORTH)
        searchPanel.add(topSplitter, BorderLayout.CENTER)

        // 하단 탭 패널 (차트 및 히트맵)
        val bottomTabbedPane = JTabbedPane().apply {
            addTab("히트맵", heatmapView)
        }

        // 메인 패널 레이아웃 (상단: 검색/관심종목, 하단: 차트/상세정보 또는 히트맵)
        val mainSplitter = com.intellij.ui.JBSplitter(true, 0.6f).apply {
            firstComponent = searchPanel
            secondComponent = bottomTabbedPane
        }
        mainPanel.add(mainSplitter, BorderLayout.CENTER)
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
                    portfolioView.updateWith(prices)
                    heatmapView.updateHeatmap(prices) // 히트맵 업데이트 추가
                }
            }
        }
    }

}