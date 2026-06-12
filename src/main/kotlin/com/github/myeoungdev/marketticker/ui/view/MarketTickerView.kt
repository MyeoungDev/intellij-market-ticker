package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.DomesticDisplayPriceSelector
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.application.service.MarketIndicatorService
import com.github.myeoungdev.marketticker.application.service.MoneyDisplayFormatter
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.github.myeoungdev.marketticker.ui.rendener.SearchResultRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Market Ticker 메인 화면입니다.
 *
 * 기본 화면에는 조회 중심 요소만 노출하고, 사용자 설정은 별도 모달에서 관리합니다.
 */
class MarketTickerView(
    private val project: Project
) : Disposable {

    private val marketDataService = service<MarketDataService>()
    private val marketIndicatorService = service<MarketIndicatorService>()
    private val localizationService = service<LocalizationService>()
    private val appSettingsService = service<AppSettingsService>()
    private val moneyDisplayFormatter = MoneyDisplayFormatter()
    private val domesticDisplayPriceSelector = DomesticDisplayPriceSelector()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var searchJob: Job = Job()

    val searchPanel = JPanel(BorderLayout())
    private val searchHeaderPanel = JPanel(BorderLayout())
    private val searchField = JBTextField()
    private val searchListModel = DefaultListModel<Ticker>()
    private val searchResultList = JBList(searchListModel)

    private val watchlistView = WatchlistView(project)
    private val portfolioView = PortfolioView()
    private val heatmapView = HeatmapView()
    private val chartView = ChartView()
    private val stockOverviewView = StockOverviewPanel()
    private val newsView = NewsView()
    private val researchView = ResearchView()
    private val screenerView = ScreenerView()
    private val calendarView = CalendarView()
    private val marketIndicatorsView = MarketIndicatorsView()
    private val stockNewsView = StockNewsSummaryPanel()
    private val stockResearchView = StockResearchSummaryPanel()

    private val marketPulseTicker = MarketPulseTicker()
    private val marketPulseContainer = JPanel(BorderLayout())
    private val watchlistPortfolioTabbedPane = JTabbedPane().apply {
        tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT
    }
    private val bottomTabbedPane = JTabbedPane().apply {
        tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT
    }
    private val mainTabbedPane = JTabbedPane().apply {
        tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT
    }
    private var latestIndicators: List<MarketIndicator> = emptyList()
    private var latestPrices: List<TickerPrice> = emptyList()

    val mainPanel = JPanel(BorderLayout())

    override fun dispose() {
        scope.cancel()
        watchlistView.dispose()
        newsView.dispose()
        researchView.dispose()
        screenerView.dispose()
        calendarView.dispose()
        marketIndicatorsView.dispose()
        chartView.dispose()
        stockOverviewView.dispose()
        stockNewsView.dispose()
        stockResearchView.dispose()
    }

    init {
        setupUI()
        setupListeners()
        observeWatchlistChanges()
    }

    private fun setupUI() {
        searchField.emptyText.text = localizationService.text(
            "종목명 또는 코드 검색 (예: 삼성전자, NVDA)",
            "Search symbol or code (e.g., Samsung, NVDA)"
        )
        searchField.margin = JBUI.insets(5)

        searchResultList.cellRenderer = SearchResultRenderer(
            moneyDisplayFormatter = moneyDisplayFormatter,
            priceLookup = { ticker ->
                latestPrices.firstOrNull {
                    it.symbol == ticker.symbol && it.marketType == ticker.marketType
                }?.let(domesticDisplayPriceSelector::select)
            }
        )

        val searchResultPanel = JBScrollPane(searchResultList).apply {
            border = javax.swing.BorderFactory.createTitledBorder(localizationService.text("검색 결과", "Search Results"))
            preferredSize = Dimension(360, 110)
        }

        rebuildWatchlistTabs()
        watchlistPortfolioTabbedPane.minimumSize = Dimension(0, 240)

        val topSplitter = com.intellij.ui.JBSplitter(true, 0.30f).apply {
            firstComponent = searchResultPanel
            secondComponent = watchlistPortfolioTabbedPane
            dividerWidth = 8
            firstComponent.minimumSize = Dimension(220, 100)
            secondComponent.minimumSize = Dimension(0, 140)
        }

        marketPulseTicker.border = JBUI.Borders.empty(4, 8)
        marketPulseContainer.add(marketPulseTicker, BorderLayout.CENTER)
        marketPulseContainer.border = JBUI.Borders.customLineTop(Color(58, 58, 58))
        marketPulseContainer.preferredSize = Dimension(0, 42)
        marketPulseContainer.minimumSize = Dimension(0, 40)
        marketPulseTicker.isVisible = appSettingsService.isMarketPulseVisible()
        marketPulseTicker.setChunks(
            listOf(
                MarketPulseTicker.Chunk(
                    localizationService.text("지표 로딩 중...", "Loading market pulse..."),
                    Color(120, 120, 120),
                    true
                )
            )
        )

        searchHeaderPanel.add(searchField, BorderLayout.CENTER)
        searchPanel.add(searchHeaderPanel, BorderLayout.NORTH)
        searchPanel.add(topSplitter, BorderLayout.CENTER)
        searchPanel.minimumSize = Dimension(0, 240)

        rebuildBottomTabs()

        val mainSplitter = com.intellij.ui.JBSplitter(true, 0.62f).apply {
            firstComponent = searchPanel
            secondComponent = bottomTabbedPane
            dividerWidth = 10
            firstComponent.minimumSize = Dimension(0, 240)
            secondComponent.minimumSize = Dimension(0, 120)
        }
        val stockPanel = JPanel(BorderLayout()).apply {
            add(mainSplitter, BorderLayout.CENTER)
            add(marketPulseContainer, BorderLayout.SOUTH)
        }
        mainTabbedPane.addTab(localizationService.text("주식", "Stocks"), stockPanel)
        mainTabbedPane.addTab(localizationService.text("스크리너", "Screener"), screenerView)
        mainTabbedPane.addTab(localizationService.text("주요 지표", "Major Indicators"), marketIndicatorsView)
        mainTabbedPane.addTab(localizationService.text("뉴스", "News"), newsView)
        mainTabbedPane.addTab(localizationService.text("리서치", "Research"), researchView)
        mainTabbedPane.addTab(localizationService.text("캘린더", "Calendar"), calendarView)
        mainPanel.add(mainTabbedPane, BorderLayout.CENTER)

        watchlistView.onTickerSelected = { ticker, _ ->
            showTickerDetails(ticker)
        }
        portfolioView.onTickerSelected = { ticker, _ ->
            showTickerDetails(ticker)
        }

        applyDisplaySettings()
        subscribeSettingsUpdates()
    }

    private fun setupListeners() {
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
                    stockOverviewView.showTicker(ticker)
                    researchView.showStockResearch(ticker)

                    searchField.text = ""
                    searchListModel.clear()
                }
            }
        })

    }

    private fun applyDisplaySettings() {
        marketPulseTicker.isVisible = true
        if (!appSettingsService.isMarketPulseVisible()) {
            marketPulseTicker.setChunks(
                listOf(
                    MarketPulseTicker.Chunk(
                        localizationService.text(
                            "한 줄 지표가 설정에서 꺼져 있습니다.",
                            "Market pulse ticker is disabled in Settings."
                        ),
                        Color(120, 120, 120),
                        false
                    )
                )
            )
        } else if (latestIndicators.isEmpty()) {
            marketPulseTicker.setChunks(
                listOf(
                    MarketPulseTicker.Chunk(
                        localizationService.text("지표 로딩 중...", "Loading market pulse..."),
                        Color(120, 120, 120),
                        true
                    )
                )
            )
        } else {
            renderMarketPulse()
        }
        refreshPriceViews()
        searchResultList.repaint()
    }

    private fun rebuildBottomTabs() {
        bottomTabbedPane.removeAll()

        bottomTabbedPane.addTab(localizationService.text("개요", "Overview"), stockOverviewView)
        bottomTabbedPane.addTab(localizationService.text("뉴스", "News"), stockNewsView)
        bottomTabbedPane.addTab(localizationService.text("리서치", "Research"), stockResearchView)

        if (appSettingsService.isChartTabVisible()) {
            bottomTabbedPane.addTab(localizationService.text("차트", "Chart"), chartView)
        }
    }

    private fun rebuildWatchlistTabs() {
        watchlistPortfolioTabbedPane.removeAll()
        watchlistPortfolioTabbedPane.addTab(localizationService.text("관심종목", "Watchlist"), watchlistView.panel)
        watchlistPortfolioTabbedPane.addTab(localizationService.text("포트폴리오", "Portfolio"), portfolioView.panel)
        if (appSettingsService.isHeatmapTabVisible()) {
            watchlistPortfolioTabbedPane.addTab(localizationService.text("관심종목 히트맵", "Watchlist Heatmap"), heatmapView)
        }
    }

    private fun subscribeSettingsUpdates() {
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(SettingsUpdateListener.TOPIC, object : SettingsUpdateListener {
            override fun onSettingsUpdated() {
                applyDisplaySettings()
                rebuildWatchlistTabs()
                rebuildBottomTabs()
                renderMarketPulse()
                marketIndicatorsView.renderIndicators(latestIndicators)
                refreshPriceViews()
                searchResultList.repaint()
            }
        })
    }

    private fun onQueryChanged() {
        val query = searchField.text.trim()

        searchJob.cancel()

        searchJob = scope.launch {
            delay(300)

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
                withContext(Dispatchers.Main) {
                    latestPrices = prices
                    val displayPrices = displayPrices()
                    watchlistView.updateWith(displayPrices)
                    portfolioView.updateWith(displayPrices)
                    heatmapView.updateHeatmap(displayPrices)
                    chartView.refreshChart()
                    searchResultList.repaint()
                }
            }
        }

        scope.launch {
            marketIndicatorService.indicators.collect { indicators ->
                withContext(Dispatchers.Main) {
                    latestIndicators = indicators
                    renderMarketPulse()
                    marketIndicatorsView.renderIndicators(indicators)
                    refreshPriceViews()
                    searchResultList.repaint()
                }
            }
        }
    }

    private fun renderMarketPulse() {
        if (!appSettingsService.isMarketPulseVisible()) {
            marketPulseTicker.setChunks(
                listOf(
                    MarketPulseTicker.Chunk(
                        localizationService.text(
                            "한 줄 지표가 설정에서 꺼져 있습니다.",
                            "Market pulse ticker is disabled in Settings."
                        ),
                        Color(120, 120, 120),
                        false
                    )
                )
            )
            return
        }
        if (latestIndicators.isEmpty()) {
            marketPulseTicker.setChunks(
                listOf(
                    MarketPulseTicker.Chunk(
                        localizationService.text("지표 로딩 중...", "Loading market pulse..."),
                        Color(120, 120, 120),
                        true
                    )
                )
            )
            return
        }

        val categoryOrder = listOf(
            IndicatorCategory.DOMESTIC_INDEX,
            IndicatorCategory.WORLD_INDEX,
            IndicatorCategory.EXCHANGE_RATE,
            IndicatorCategory.METAL,
            IndicatorCategory.ENERGY
        )

        val chunks = mutableListOf<MarketPulseTicker.Chunk>()
        val visibleCategories = categoryOrder.filter { category ->
            latestIndicators.any { it.category == category }
        }
        visibleCategories.forEachIndexed { index, category ->
            val title = when (category) {
                IndicatorCategory.DOMESTIC_INDEX -> localizationService.text("국내", "KR")
                IndicatorCategory.WORLD_INDEX -> localizationService.text("해외", "US")
                IndicatorCategory.EXCHANGE_RATE -> localizationService.text("환율", "FX")
                IndicatorCategory.METAL -> localizationService.text("금속", "Metal")
                IndicatorCategory.ENERGY -> localizationService.text("에너지", "Energy")
            }

            val indicators = latestIndicators.filter { it.category == category }
            if (indicators.isNotEmpty()) {
                chunks += MarketPulseTicker.Chunk("[$title] ", Color(140, 140, 140), true)
                indicators.forEach { indicator ->
                    val sign = if (indicator.changeRate > 0) "+" else ""
                    val rateText = if (indicator.changeRate.isFinite()) {
                        "${sign}${localizationService.formatPercentFixed(indicator.changeRate, 2)}"
                    } else localizationService.text("N/A", "N/A")
                    val priceText = localizationService.formatDecimal(indicator.currentPrice, 2)
                    val rateColor = when {
                        indicator.changeRate > 0 -> Color(217, 48, 37)
                        indicator.changeRate < 0 -> Color(26, 115, 232)
                        else -> Color(120, 120, 120)
                    }
                    val displayName = displayIndicatorName(indicator)
                    val tickerText = "$displayName $priceText ($rateText) "

                    chunks += MarketPulseTicker.Chunk(tickerText, rateColor, true)
                    chunks += MarketPulseTicker.Chunk("   ", Color(120, 120, 120), false)
                }
            }

            if (index < visibleCategories.lastIndex) {
                chunks += MarketPulseTicker.Chunk(" | ", Color(100, 100, 100), true)
            }
        }

        if (chunks.none { it.text.isNotBlank() && it.text.trim() != "|" }) {
            marketPulseTicker.setChunks(
                listOf(
                    MarketPulseTicker.Chunk(
                        localizationService.text("지표 데이터가 없습니다.", "No market pulse data."),
                        Color(120, 120, 120),
                        false
                    )
                )
            )
            return
        }

        marketPulseTicker.setChunks(chunks)
    }

    private fun displayIndicatorName(indicator: MarketIndicator): String {
        return when (indicator.code.uppercase()) {
            ".INX", "SPX", "S&P500", "S&P 500" -> "S&P500"
            ".IXIC", "IXIC", "NASDAQ", "NASDAQ COMPOSITE" -> "NASDAQ"
            ".DJI", "DJI", "DJIA", "DOW JONES" -> "DOW"
            else -> indicator.name
        }
    }

    private fun refreshPriceViews() {
        val displayPrices = displayPrices()
        watchlistView.updateWith(displayPrices)
        portfolioView.updateWith(displayPrices)
        heatmapView.updateHeatmap(displayPrices)
        chartView.refreshChart()
    }

    private fun displayPrices(): List<TickerPrice> {
        return latestPrices.map(domesticDisplayPriceSelector::select)
    }

    private fun showTickerDetails(ticker: Ticker) {
        chartView.updateSelection(ticker)
        stockOverviewView.showTicker(ticker)
        researchView.showStockResearch(ticker)
        stockNewsView.showTicker(ticker)
        stockResearchView.showTicker(ticker)
    }

}
