package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.MoneyDisplayFormatter
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.application.service.MarketIndicatorService
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.github.myeoungdev.marketticker.ui.rendener.SearchResultRenderer
import com.intellij.icons.AllIcons
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
import java.awt.FlowLayout
import javax.swing.DefaultListModel
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JToggleButton
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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var searchJob: Job = Job()

    val searchPanel = JPanel(BorderLayout())
    private val searchHeaderPanel = JPanel(BorderLayout())
    private val displayModePanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
    private val displayModeLabel = JLabel()
    private val mixedDisplayButton = JToggleButton()
    private val krwDisplayButton = JToggleButton()
    private val quickToggleButton = JToggleButton(AllIcons.Actions.ToggleVisibility)
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
    private val stockNewsView = StockNewsSummaryPanel()
    private val stockResearchView = StockResearchSummaryPanel()

    private val marketPulseTicker = MarketPulseTicker()
    private val marketPulseContainer = JPanel(BorderLayout())
    private val watchlistPortfolioTabbedPane = JTabbedPane()
    private val bottomTabbedPane = JTabbedPane()
    private val topLevelTabbedPane = JTabbedPane()
    private val mainTabbedPane = JTabbedPane()
    private var latestIndicators: List<MarketIndicator> = emptyList()
    private var latestPrices: List<TickerPrice> = emptyList()

    val mainPanel = JPanel(BorderLayout())

    override fun dispose() {
        scope.cancel()
        newsView.dispose()
        researchView.dispose()
        screenerView.dispose()
        calendarView.dispose()
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
                }
            }
        )

        val searchResultPanel = JBScrollPane(searchResultList).apply {
            border = javax.swing.BorderFactory.createTitledBorder(localizationService.text("검색 결과", "Search Results"))
        }

        rebuildWatchlistTabs()

        val topSplitter = com.intellij.ui.JBSplitter(true, 0.4f).apply {
            firstComponent = searchResultPanel
            secondComponent = watchlistPortfolioTabbedPane
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

        setupDisplayModePanel()
        searchHeaderPanel.add(displayModePanel, BorderLayout.NORTH)
        searchHeaderPanel.add(searchField, BorderLayout.CENTER)
        searchPanel.add(searchHeaderPanel, BorderLayout.NORTH)
        searchPanel.add(topSplitter, BorderLayout.CENTER)

        rebuildBottomTabs()

        val mainSplitter = com.intellij.ui.JBSplitter(true, 0.72f).apply {
            firstComponent = searchPanel
            secondComponent = bottomTabbedPane
            dividerWidth = 10
        }
        val stockPanel = JPanel(BorderLayout()).apply {
            add(mainSplitter, BorderLayout.CENTER)
            add(marketPulseContainer, BorderLayout.SOUTH)
        }
        mainTabbedPane.addTab(localizationService.text("주식", "Stocks"), stockPanel)
        mainTabbedPane.addTab(localizationService.text("스크리너", "Screener"), screenerView)
        mainTabbedPane.addTab(localizationService.text("캘린더", "Calendar"), calendarView)
        mainTabbedPane.addTab(localizationService.text("뉴스", "News"), newsView)
        mainTabbedPane.addTab(localizationService.text("리서치", "Research"), researchView)
        mainPanel.add(mainTabbedPane, BorderLayout.CENTER)

        watchlistView.onTickerSelected = { ticker, _ ->
            chartView.updateSelection(ticker)
            stockOverviewView.showTicker(ticker)
            researchView.showStockResearch(ticker)
            stockNewsView.showTicker(ticker)
            stockResearchView.showTicker(ticker)
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

    private fun setupDisplayModePanel() {
        val group = ButtonGroup()

        group.add(mixedDisplayButton)
        group.add(krwDisplayButton)

        refreshDisplayModePanelLabels()
        displayModePanel.add(displayModeLabel)
        displayModePanel.add(mixedDisplayButton)
        displayModePanel.add(krwDisplayButton)
        quickToggleButton.toolTipText = localizationService.text(
            "표시 모드 빠른 전환",
            "Quick toggle between mixed and converted display"
        )
        quickToggleButton.isFocusable = false
        displayModePanel.add(quickToggleButton)

        mixedDisplayButton.addActionListener {
            if (mixedDisplayButton.isSelected) {
                updatePriceDisplayMode(AppSettingsService.PriceDisplayMode.MIXED)
            }
        }

        krwDisplayButton.addActionListener {
            if (krwDisplayButton.isSelected) {
                updatePriceDisplayMode(AppSettingsService.PriceDisplayMode.KRW_CONVERTED)
            }
        }

        quickToggleButton.addActionListener {
            val next = if (appSettingsService.getPriceDisplayMode() == AppSettingsService.PriceDisplayMode.MIXED) {
                AppSettingsService.PriceDisplayMode.KRW_CONVERTED
            } else {
                AppSettingsService.PriceDisplayMode.MIXED
            }
            updatePriceDisplayMode(next)
        }

        syncDisplayModeButtons()
    }

    private fun applyDisplaySettings() {
        marketPulseTicker.isVisible = true
        refreshDisplayModePanelLabels()
        syncDisplayModeButtons()
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
                    watchlistView.updateWith(prices)
                    portfolioView.updateWith(prices)
                    heatmapView.updateHeatmap(prices)
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
        watchlistView.updateWith(latestPrices)
        portfolioView.updateWith(latestPrices)
        heatmapView.updateHeatmap(latestPrices)
        chartView.refreshChart()
    }

    private fun refreshDisplayModePanelLabels() {
        displayModeLabel.text = localizationService.text("표시", "Display")
        mixedDisplayButton.text = localizationService.text("혼용", "Mixed")
        krwDisplayButton.text = localizationService.text("기준 통화", "Converted")
        mixedDisplayButton.toolTipText = localizationService.text("원문 통화로 표시", "Show native currency")
        krwDisplayButton.toolTipText = localizationService.text("기준 통화로 환산해 표시", "Convert values to the selected base currency")
    }

    private fun syncDisplayModeButtons() {
        val mode = appSettingsService.getPriceDisplayMode()
        mixedDisplayButton.isSelected = mode == AppSettingsService.PriceDisplayMode.MIXED
        krwDisplayButton.isSelected = mode == AppSettingsService.PriceDisplayMode.KRW_CONVERTED
        quickToggleButton.isSelected = mode == AppSettingsService.PriceDisplayMode.KRW_CONVERTED
    }

    private fun updatePriceDisplayMode(mode: AppSettingsService.PriceDisplayMode) {
        if (appSettingsService.getPriceDisplayMode() == mode) {
            return
        }

        appSettingsService.setPriceDisplayMode(mode)
        ApplicationManager.getApplication().messageBus
            .syncPublisher(SettingsUpdateListener.TOPIC)
            .onSettingsUpdated()
    }
}
