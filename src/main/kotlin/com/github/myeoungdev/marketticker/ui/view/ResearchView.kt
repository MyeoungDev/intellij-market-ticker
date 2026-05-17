package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.model.research.ResearchHomeViewData
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.ResearchFacadeService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.research.ResearchArticle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchCategory
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingType
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.ListSelectionModel

class ResearchView : JPanel(BorderLayout()) {

    private enum class SourceTab {
        CORE,
        RANKING,
        STOCK
    }

    private val localizationService = service<LocalizationService>()
    private val researchFacadeService = service<ResearchFacadeService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var stockSearchJob: Job = Job()

    private val refreshButton = JButton(localizationService.text("새로고침", "Refresh"))
    private val openButton = JButton(localizationService.text("원문 열기", "Open"))
    private val statusLabel = JLabel(localizationService.text("리서치를 불러오는 중...", "Loading research..."))

    private val coreCategoryCombo = JComboBox(DefaultComboBoxModel(ResearchCategory.values()))
    private val rankingTypeCombo = JComboBox(DefaultComboBoxModel(ResearchRankingType.values()))
    private val rankingRankCombo = JComboBox(DefaultComboBoxModel(arrayOf(1, 2, 3, 4, 5)))
    private val stockCodeField = JBTextField()
    private val stockSearchResultModel = CollectionListModel<Ticker>()
    private val stockSearchResultList = JBList(stockSearchResultModel)

    private val coreModel = CollectionListModel<ResearchArticle>()
    private val rankingModel = CollectionListModel<ResearchArticle>()
    private val stockModel = CollectionListModel<ResearchArticle>()

    private val coreList = createResearchList(coreModel)
    private val rankingList = createResearchList(rankingModel)
    private val stockList = createResearchList(stockModel)

    private val tabs = JTabbedPane().apply {
        tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT
    }
    private val detailTitleLabel = JLabel(localizationService.text("리서치를 선택하세요", "Select research"))
    private val detailMetaLabel = JLabel(" ")
    private val detailBodyPane = JEditorPane("text/html", "")

    private var latestData: Map<ResearchCategory, List<ResearchArticle>> = emptyMap()
    private var rankingData: List<ResearchArticle> = emptyList()
    private var stockData: List<ResearchArticle> = emptyList()
    private var selectedArticle: ResearchArticle? = null

    init {
        border = JBUI.Borders.empty(10)
        setupUi()
        setupListeners()
        refreshAll(forceRefresh = false)
    }

    fun dispose() {
        stockSearchJob.cancel()
        scope.cancel()
    }

    private fun setupUi() {
        stockCodeField.emptyText.text = localizationService.text(
            "종목명 또는 코드 검색 후 선택 (예: 삼성전자, 005930)",
            "Search name or code, then pick a ticker (e.g. Samsung, 005930)"
        )
        stockSearchResultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        stockSearchResultList.visibleRowCount = 4
        stockSearchResultList.cellRenderer = SimpleListCellRenderer.create { label, value, _ ->
            val item = value ?: return@create
            val meta = listOfNotNull(item.symbol, item.marketType.name).joinToString(" · ")
            label.border = JBUI.Borders.empty(4, 6)
            label.text = """
                <html>
                <div><b>${escapeHtml(item.name)}</b></div>
                <div style="color:#8a8a8a; margin-top:2px;">${escapeHtml(meta)}</div>
                </html>
            """.trimIndent()
        }

        add(buildToolbar(), BorderLayout.NORTH)
        add(buildCenterPanel(), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun buildToolbar(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(
                JLabel(localizationService.text("리서치", "Research")).apply {
                    font = font.deriveFont(font.style or java.awt.Font.BOLD, font.size2D + 2f)
                },
                BorderLayout.WEST
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(openButton)
                    add(refreshButton)
                },
                BorderLayout.EAST
            )
        }
    }

    private fun buildCenterPanel(): JPanel {
        detailBodyPane.isEditable = false
        detailBodyPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)

        val corePanel = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(0, 8, 8, 8)
            add(coreCategoryCombo, BorderLayout.NORTH)
            add(JBScrollPane(coreList), BorderLayout.CENTER)
        }

        val rankingPanel = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(0, 8, 8, 8)
            add(
                JPanel(BorderLayout(8, 0)).apply {
                    add(rankingTypeCombo, BorderLayout.CENTER)
                    add(rankingRankCombo, BorderLayout.EAST)
                },
                BorderLayout.NORTH
            )
            add(JBScrollPane(rankingList), BorderLayout.CENTER)
        }

        val stockPanel = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(0, 8, 8, 8)
            add(
                JPanel(BorderLayout(0, 8)).apply {
                    add(stockCodeField, BorderLayout.NORTH)
                    add(JBScrollPane(stockSearchResultList).apply {
                        preferredSize = Dimension(0, 96)
                        minimumSize = Dimension(0, 84)
                    }, BorderLayout.CENTER)
                },
                BorderLayout.NORTH
            )
            add(JBScrollPane(stockList), BorderLayout.CENTER)
        }

        tabs.addTab(localizationService.text("핵심 리서치", "Core"), corePanel)
        tabs.addTab(localizationService.text("랭킹 리서치", "Ranking"), rankingPanel)
        tabs.addTab(localizationService.text("국내 종목 리서치", "Domestic Stock"), stockPanel)

        val detailPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLineTop(JBColor.border()),
                JBUI.Borders.empty(8)
            )
            preferredSize = Dimension(0, 220)
            minimumSize = Dimension(0, 220)
            maximumSize = Dimension(Int.MAX_VALUE, 220)

            val header = JPanel(VerticalLayout(4)).apply {
                isOpaque = false
                add(detailTitleLabel)
                add(detailMetaLabel)
            }

            add(header, BorderLayout.NORTH)
            add(
                JBScrollPane(detailBodyPane).apply {
                    border = BorderFactory.createEmptyBorder()
                    viewport.background = JBColor.PanelBackground
                },
                BorderLayout.CENTER
            )
        }

        detailTitleLabel.font = detailTitleLabel.font.deriveFont(detailTitleLabel.font.size2D + 1f)
        detailMetaLabel.foreground = JBColor.GRAY
        detailBodyPane.isOpaque = true
        detailBodyPane.background = JBColor.PanelBackground
        detailBodyPane.foreground = JBColor.foreground()
        detailBodyPane.border = JBUI.Borders.empty(10)

        return JPanel(BorderLayout(0, 10)).apply {
            add(tabs, BorderLayout.CENTER)
            add(detailPanel, BorderLayout.SOUTH)
        }
    }

    private fun setupListeners() {
        coreList.addListSelectionListener { if (!it.valueIsAdjusting) updateDetail(coreList.selectedValue) }
        rankingList.addListSelectionListener { if (!it.valueIsAdjusting) updateDetail(rankingList.selectedValue) }
        stockList.addListSelectionListener { if (!it.valueIsAdjusting) updateDetail(stockList.selectedValue) }
        tabs.addChangeListener {
            ensureSelection(currentList())
            updateDetail(currentList().selectedValue)
            if (currentSource() == SourceTab.RANKING) {
                refreshRankingResearch(forceRefresh = false)
            }
        }

        coreCategoryCombo.addActionListener { updateCoreList() }
        rankingTypeCombo.addActionListener { refreshRankingResearch(forceRefresh = false) }
        rankingRankCombo.addActionListener { refreshRankingResearch(forceRefresh = false) }
        refreshButton.addActionListener { refreshAll(forceRefresh = true) }
        openButton.addActionListener {
            selectedArticle?.endUrl?.takeIf { it.isNotBlank() }?.let(BrowserUtil::browse)
        }
        stockCodeField.addActionListener {
            val selected = stockSearchResultList.selectedValue
                ?: stockSearchResultList.model.takeIf { it.size > 0 }?.getElementAt(0)
            if (selected != null) {
                showStockResearch(selected.name, selected.symbol, selected.name)
            }
        }
        stockCodeField.document.addDocumentListener(simpleDocumentListener { refreshStockSuggestions() })
        stockSearchResultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = stockSearchResultList.selectedValue ?: return
                    showStockResearch(selected.name, selected.symbol, selected.name)
                }
            }
        })

        coreList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    coreList.selectedValue?.let { article ->
                        showStockResearch(article.itemName ?: article.itemCode.orEmpty(), article.itemCode, article.itemName)
                    }
                }
            }
        })
        rankingList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    rankingList.selectedValue?.let { article ->
                        showStockResearch(article.itemName ?: article.itemCode.orEmpty(), article.itemCode, article.itemName)
                    }
                }
            }
        })
    }

    fun showStockResearch(ticker: Ticker) {
        showStockResearch(ticker.name, ticker.symbol, ticker.name)
    }

    fun showStockResearch(query: String, preferredItemCode: String? = null, preferredName: String? = null) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank() && preferredItemCode.isNullOrBlank()) return

        stockCodeField.text = preferredName?.takeIf { it.isNotBlank() } ?: preferredItemCode ?: normalizedQuery
        tabs.selectedIndex = SourceTab.STOCK.ordinal
        loadStockResearch(normalizedQuery.ifBlank { preferredItemCode.orEmpty() }, preferredItemCode, preferredName, false)
    }

    private fun refreshAll(forceRefresh: Boolean) {
        setLoadingState()

        scope.launch {
            val homeData = researchFacadeService.loadResearchHome(forceRefresh = forceRefresh)
            withContext(Dispatchers.Main) {
                applyHomeData(homeData)
            }
        }
    }

    private fun applyHomeData(homeData: ResearchHomeViewData) {
        latestData = homeData.latestByCategory
        rankingData = homeData.rankingArticles
        stockData = emptyList()
        stockSearchResultModel.replaceAll(emptyList())

        updateCoreList()
        updateRankingList()
        updateStockList()

        statusLabel.text = localizationService.text(
            "핵심 ${coreModel.size}건 · 랭킹 ${rankingModel.size}건 · 국내 종목 ${stockModel.size}건",
            "Core ${coreModel.size} · Ranking ${rankingModel.size} · Domestic stock ${stockModel.size}"
        )

        if (currentSource() == SourceTab.RANKING) {
            refreshRankingResearch(forceRefresh = false)
        }
    }

    private fun setLoadingState() {
        val loading = listOf(
            ResearchArticle(title = localizationService.text("리서치 로딩 중...", "Loading research..."))
        )
        coreModel.replaceAll(loading)
        rankingModel.replaceAll(loading)
        stockModel.replaceAll(
            listOf(
                ResearchArticle(
                    title = localizationService.text(
                        "국내 종목을 검색하고 선택하면 리서치를 불러옵니다.",
                        "Search and select a domestic ticker to load research"
                    )
                )
            )
        )
        updateDetail(null)
    }

    private fun updateCoreList() {
        val key = coreCategoryCombo.selectedItem as? ResearchCategory ?: ResearchCategory.MARKET
        coreModel.replaceAll(latestData[key].orEmpty())
        ensureSelection(coreList)
    }

    private fun updateRankingList() {
        rankingModel.replaceAll(rankingData)
        ensureSelection(rankingList)
    }

    private fun updateStockList() {
        stockModel.replaceAll(stockData)
        ensureSelection(stockList)
    }

    private fun refreshRankingResearch(forceRefresh: Boolean) {
        val rankingType = rankingTypeCombo.selectedItem as? ResearchRankingType ?: ResearchRankingType.SEARCH_TOP
        val selectedRank = rankingRankCombo.selectedItem as? Int ?: 1

        rankingModel.replaceAll(
            listOf(ResearchArticle(title = localizationService.text("랭킹 리서치 로딩 중...", "Loading ranking research...")))
        )

        scope.launch {
            val articles = runCatching {
                researchFacadeService.loadRankingResearch(rankingType, selectedRank, forceRefresh)
            }.getOrElse {
                withContext(Dispatchers.Main) {
                    rankingData = listOf(
                        ResearchArticle(
                            title = localizationService.text(
                                "랭킹 리서치를 불러오지 못했습니다. 잠시 후 다시 시도하세요.",
                                "Failed to load ranking research. Try again shortly."
                            )
                        )
                    )
                    updateRankingList()
                    statusLabel.text = localizationService.text(
                        "랭킹 리서치 로딩 실패",
                        "Ranking research load failed"
                    )
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                rankingData = articles
                updateRankingList()
                statusLabel.text = localizationService.text(
                    "${rankingType.label} ${selectedRank}위 리서치 ${rankingData.size}건",
                    "${rankingType.label} rank ${selectedRank}: ${rankingData.size} items"
                )
            }
        }
    }

    private fun loadStockResearch(
        query: String,
        preferredItemCode: String? = null,
        preferredName: String? = null,
        forceRefresh: Boolean
    ) {
        stockModel.replaceAll(
            listOf(ResearchArticle(title = localizationService.text("국내 종목 리서치 로딩 중...", "Loading domestic stock research...")))
        )

        scope.launch {
            val result = researchFacadeService.loadStockResearch(query, preferredItemCode, preferredName, forceRefresh)
            withContext(Dispatchers.Main) {
                if (result.resolvedTicker == null) {
                    stockData = listOf(
                        ResearchArticle(
                            title = localizationService.text("일치하는 국내 종목이 없습니다.", "No matching domestic stock found.")
                        )
                    )
                    updateStockList()
                    statusLabel.text = localizationService.text(
                        "'$query' 검색 결과가 없습니다.",
                        "No search result for '$query'."
                    )
                    return@withContext
                }

                val resolved = result.resolvedTicker
                val stockResearch = result.articles
                stockData = stockResearch.ifEmpty {
                    listOf(
                        ResearchArticle(
                            title = localizationService.text("해당 종목 리서치가 없습니다.", "No research available for this stock."),
                            itemCode = resolved.symbol,
                            itemName = resolved.name
                        )
                    )
                }
                updateStockList()
                stockCodeField.text = resolved.name
                stockSearchResultModel.replaceAll(emptyList())
                statusLabel.text = localizationService.text(
                    "${resolved.name}(${resolved.symbol}) 국내 종목 리서치 ${stockResearch.size}건",
                    "${resolved.name} (${resolved.symbol}) domestic stock research ${stockResearch.size} items"
                )
            }
        }
    }

    private fun refreshStockSuggestions() {
        val query = stockCodeField.text.trim()
        if (query.isBlank()) {
            stockSearchJob.cancel()
            stockSearchResultModel.replaceAll(emptyList())
            return
        }

        stockSearchJob.cancel()
        stockSearchJob = scope.launch {
            delay(250)
            val matches = researchFacadeService.searchStockSuggestions(query)
            withContext(Dispatchers.Main) {
                if (stockCodeField.text.trim() == query) {
                    stockSearchResultModel.replaceAll(matches)
                    if (matches.isNotEmpty()) {
                        stockSearchResultList.selectedIndex = 0
                    }
                }
            }
        }
    }

    private fun updateDetail(article: ResearchArticle?) {
        selectedArticle = article

        if (article == null) {
            detailTitleLabel.text = localizationService.text("리서치를 선택하세요", "Select research")
            detailMetaLabel.text = " "
            detailBodyPane.text = emptyHtmlBody(localizationService.text("표시할 리서치가 없습니다.", "Nothing to show"))
            openButton.isEnabled = false
            return
        }

        detailTitleLabel.text = article.title
        detailMetaLabel.text = buildMetaText(article)
        detailBodyPane.text = buildHtmlBody(article)
        detailBodyPane.caretPosition = 0
        openButton.isEnabled = article.endUrl.isNotBlank()
    }

    private fun buildMetaText(article: ResearchArticle): String {
        val parts = mutableListOf<String>()
        if (article.category.isNotBlank()) parts += article.category
        if (article.itemName?.isNotBlank() == true) parts += "${article.itemName}(${article.itemCode ?: "-"})"
        if (article.brokerName.isNotBlank()) parts += article.brokerName
        if (article.analyst?.isNotBlank() == true) parts += article.analyst
        if (article.writeDate.isNotBlank()) parts += formatDate(article.writeDate)
        if (article.readCount?.isNotBlank() == true) parts += "조회 ${article.readCount}"
        if (article.opinion?.isNotBlank() == true) parts += "의견 ${article.opinion}"
        if (article.goalPrice?.isNotBlank() == true) parts += "목표가 ${article.goalPrice}"
        return parts.joinToString(" · ").ifBlank { " " }
    }

    private fun buildHtmlBody(article: ResearchArticle): String {
        val content = article.content.takeIf { it.isNotBlank() }
            ?: "<p>${localizationService.text("본문 미리보기가 없습니다.", "No preview available.")}</p>"

        return """
            <html>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; font-size: 13px; line-height: 1.55; margin: 0; color: #d7dae0; background: #2b2d30;">
                $content
            </body>
            </html>
        """.trimIndent()
    }

    private fun emptyHtmlBody(message: String): String {
        return """
            <html>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; font-size: 13px; margin: 0; color: #888888; background: #2b2d30;">
                <p>$message</p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun currentSource(): SourceTab {
        return when (tabs.selectedIndex) {
            1 -> SourceTab.RANKING
            2 -> SourceTab.STOCK
            else -> SourceTab.CORE
        }
    }

    private fun currentList(): JBList<ResearchArticle> {
        return when (currentSource()) {
            SourceTab.CORE -> coreList
            SourceTab.RANKING -> rankingList
            SourceTab.STOCK -> stockList
        }
    }

    private fun createResearchList(model: CollectionListModel<ResearchArticle>): JBList<ResearchArticle> {
        return JBList(model).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = SimpleListCellRenderer.create { label, value, _ ->
                label.border = JBUI.Borders.empty(8, 6)
                label.text = formatListText(value)
            }
        }
    }

    private fun ensureSelection(list: JBList<ResearchArticle>) {
        if (list.model.size > 0 && list.selectedIndex == -1) {
            list.selectedIndex = 0
        } else if (list.model.size == 0 && currentList() === list) {
            updateDetail(null)
        }
    }

    private fun simpleDocumentListener(onChange: () -> Unit) = object : javax.swing.event.DocumentListener {
        override fun insertUpdate(e: javax.swing.event.DocumentEvent) = onChange()
        override fun removeUpdate(e: javax.swing.event.DocumentEvent) = onChange()
        override fun changedUpdate(e: javax.swing.event.DocumentEvent) = onChange()
    }

    private fun formatListText(article: ResearchArticle?): String {
        if (article == null) return ""

        val meta = listOfNotNull(
            article.itemName?.takeIf { it.isNotBlank() },
            article.brokerName.takeIf { it.isNotBlank() },
            article.writeDate.takeIf { it.isNotBlank() }?.let(::formatDate)
        ).joinToString(" · ")

        return """
            <html>
            <div style="margin-bottom:4px;"><b>${escapeHtml(article.title)}</b></div>
            <div style="color:#8a8a8a;">${escapeHtml(meta)}</div>
            </html>
        """.trimIndent()
    }

    private fun formatDate(value: String): String {
        return runCatching {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).toString()
        }.getOrDefault(value)
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
