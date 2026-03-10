package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverClient
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchArticle
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchRankingResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverSearchItem
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchCategoryKey
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchRankingType
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ResearchView : JPanel(BorderLayout()) {

    private data class ResolvedStock(
        val itemCode: String,
        val itemName: String
    )

    private enum class SourceTab {
        CORE,
        RANKING,
        STOCK
    }

    private val localizationService = service<LocalizationService>()
    private val naverClient = NaverClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val refreshButton = JButton(localizationService.text("새로고침", "Refresh"))
    private val openButton = JButton(localizationService.text("원문 열기", "Open"))
    private val relatedStockButton = JButton(localizationService.text("종목 리서치", "Stock Research"))
    private val filterField = JBTextField()
    private val statusLabel = JLabel(localizationService.text("리서치를 불러오는 중...", "Loading research..."))

    private val coreCategoryCombo = JComboBox(DefaultComboBoxModel(ResearchCategoryKey.values()))
    private val rankingTypeCombo = JComboBox(DefaultComboBoxModel(ResearchRankingType.values()))
    private val rankingRankCombo = JComboBox(DefaultComboBoxModel(arrayOf(1, 2, 3, 4, 5)))
    private val stockCodeField = JBTextField()
    private val stockResearchLoadButton = JButton(localizationService.text("종목 조회", "Load"))

    private val coreModel = CollectionListModel<NaverResearchArticle>()
    private val rankingModel = CollectionListModel<NaverResearchArticle>()
    private val stockModel = CollectionListModel<NaverResearchArticle>()

    private val coreList = createResearchList(coreModel)
    private val rankingList = createResearchList(rankingModel)
    private val stockList = createResearchList(stockModel)

    private val tabs = JTabbedPane()
    private val detailTitleLabel = JLabel(localizationService.text("리서치를 선택하세요", "Select research"))
    private val detailMetaLabel = JLabel(" ")
    private val detailBodyPane = JEditorPane("text/html", "")

    private var latestData: Map<ResearchCategoryKey, List<NaverResearchArticle>> = emptyMap()
    private var rankingData: List<NaverResearchArticle> = emptyList()
    private var stockData: List<NaverResearchArticle> = emptyList()
    private var selectedArticle: NaverResearchArticle? = null

    init {
        border = JBUI.Borders.empty(10)
        setupUi()
        setupListeners()
        refreshAll()
    }

    fun dispose() {
        scope.cancel()
    }

    private fun setupUi() {
        filterField.emptyText.text = localizationService.text(
            "리서치 검색 (제목, 종목, 증권사)",
            "Filter research (title, symbol, broker)"
        )
        stockCodeField.emptyText.text = localizationService.text(
            "종목명 또는 코드 입력 (예: 삼성전자, 005930)",
            "Enter item name or code (e.g. Samsung, 005930)"
        )

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
                    add(filterField)
                    add(relatedStockButton)
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
                JPanel(BorderLayout(8, 0)).apply {
                    add(stockCodeField, BorderLayout.CENTER)
                    add(stockResearchLoadButton, BorderLayout.EAST)
                },
                BorderLayout.NORTH
            )
            add(JBScrollPane(stockList), BorderLayout.CENTER)
        }

        tabs.addTab(localizationService.text("핵심 리서치", "Core"), corePanel)
        tabs.addTab(localizationService.text("랭킹 리서치", "Ranking"), rankingPanel)
        tabs.addTab(localizationService.text("종목 리서치", "Stock"), stockPanel)

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
            add(JBScrollPane(detailBodyPane), BorderLayout.CENTER)
        }

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
            applyFilter()
            updateDetail(currentList().selectedValue)
        }

        coreCategoryCombo.addActionListener { updateCoreList() }
        rankingTypeCombo.addActionListener { refreshRankingResearch() }
        rankingRankCombo.addActionListener { refreshRankingResearch() }
        stockResearchLoadButton.addActionListener { refreshStockResearch() }
        refreshButton.addActionListener { refreshAll() }
        openButton.addActionListener {
            selectedArticle?.endUrl?.takeIf { it.isNotBlank() }?.let(BrowserUtil::browse)
        }
        relatedStockButton.addActionListener {
            selectedArticle?.let { article ->
                showStockResearch(article.itemName ?: article.itemCode.orEmpty(), article.itemCode, article.itemName)
            }
        }

        filterField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilter()
            override fun removeUpdate(e: DocumentEvent) = applyFilter()
            override fun changedUpdate(e: DocumentEvent) = applyFilter()
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
        loadStockResearch(normalizedQuery.ifBlank { preferredItemCode.orEmpty() }, preferredItemCode, preferredName)
    }

    private fun refreshAll() {
        setLoadingState()

        scope.launch {
            val latest = naverClient.fetchCategoryLatestResearch()
            val ranking = naverClient.fetchResearchRanking(ResearchRankingType.SEARCH_TOP, 1)

            withContext(Dispatchers.Main) {
                latestData = latest.categoryMap()
                rankingData = ranking.latestResearch.map { article ->
                    article.copy(
                        category = ResearchRankingType.SEARCH_TOP.label,
                        analyst = buildRankingMeta(ranking, 1)
                    )
                }
                stockData = emptyList()

                updateCoreList()
                updateRankingList()
                updateStockList()

                statusLabel.text = localizationService.text(
                    "핵심 ${coreModel.size}건 · 랭킹 ${rankingModel.size}건 · 종목 ${stockModel.size}건",
                    "Core ${coreModel.size} · Ranking ${rankingModel.size} · Stock ${stockModel.size}"
                )
            }
        }
    }

    private fun setLoadingState() {
        val loading = listOf(
            NaverResearchArticle(title = localizationService.text("리서치 로딩 중...", "Loading research..."))
        )
        coreModel.replaceAll(loading)
        rankingModel.replaceAll(loading)
        stockModel.replaceAll(
            listOf(
                NaverResearchArticle(
                    title = localizationService.text("종목 코드를 입력한 뒤 조회하세요", "Enter an item code and load research")
                )
            )
        )
        updateDetail(null)
    }

    private fun updateCoreList() {
        val key = coreCategoryCombo.selectedItem as? ResearchCategoryKey ?: ResearchCategoryKey.MARKET
        coreModel.replaceAll(latestData[key].orEmpty())
        applyFilter()
    }

    private fun updateRankingList() {
        rankingModel.replaceAll(rankingData)
        applyFilter()
    }

    private fun updateStockList() {
        stockModel.replaceAll(stockData)
        applyFilter()
    }

    private fun refreshRankingResearch() {
        val rankingType = rankingTypeCombo.selectedItem as? ResearchRankingType ?: ResearchRankingType.SEARCH_TOP
        val selectedRank = rankingRankCombo.selectedItem as? Int ?: 1

        rankingModel.replaceAll(
            listOf(NaverResearchArticle(title = localizationService.text("랭킹 리서치 로딩 중...", "Loading ranking research...")))
        )

        scope.launch {
            val ranking = naverClient.fetchResearchRanking(rankingType, selectedRank)
            withContext(Dispatchers.Main) {
                rankingData = ranking.latestResearch.map { article ->
                    article.copy(
                        category = rankingType.label,
                        analyst = buildRankingMeta(ranking, selectedRank)
                    )
                }
                updateRankingList()
                statusLabel.text = localizationService.text(
                    "${rankingType.label} ${selectedRank}위 리서치 ${rankingData.size}건",
                    "${rankingType.label} rank ${selectedRank}: ${rankingData.size} items"
                )
            }
        }
    }

    private fun refreshStockResearch() {
        val query = stockCodeField.text.trim()
        if (query.isBlank()) return

        loadStockResearch(query)
    }

    private fun loadStockResearch(query: String, preferredItemCode: String? = null, preferredName: String? = null) {
        stockModel.replaceAll(
            listOf(NaverResearchArticle(title = localizationService.text("종목 리서치 로딩 중...", "Loading stock research...")))
        )

        scope.launch {
            val resolved = resolveStock(query, preferredItemCode, preferredName)
            withContext(Dispatchers.Main) {
                if (resolved == null) {
                    stockData = listOf(
                        NaverResearchArticle(
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
            }
            val resolvedStock = resolved ?: return@launch

            val stockResearch = naverClient.fetchStockResearch(resolvedStock.itemCode)

            withContext(Dispatchers.Main) {
                stockData = stockResearch.ifEmpty {
                    listOf(
                        NaverResearchArticle(
                            title = localizationService.text("해당 종목 리서치가 없습니다.", "No research available for this stock."),
                            itemCode = resolvedStock.itemCode,
                            itemName = resolvedStock.itemName
                        )
                    )
                }
                updateStockList()
                stockCodeField.text = resolvedStock.itemName
                statusLabel.text = localizationService.text(
                    "${resolvedStock.itemName}(${resolvedStock.itemCode}) 종목 리서치 ${stockResearch.size}건",
                    "${resolvedStock.itemName} (${resolvedStock.itemCode}) stock research ${stockResearch.size} items"
                )
            }
        }
    }

    private fun resolveStock(query: String, preferredItemCode: String?, preferredName: String?): ResolvedStock? {
        preferredItemCode?.takeIf { it.isNotBlank() }?.let { code ->
            return ResolvedStock(code, preferredName?.takeIf { it.isNotBlank() } ?: code)
        }

        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return null

        val results = naverClient.searchStocks(normalizedQuery)
        val stockItem = pickBestStockMatch(results, normalizedQuery) ?: return null
        return ResolvedStock(stockItem.code, stockItem.name)
    }

    private fun pickBestStockMatch(results: List<NaverSearchItem>, query: String): NaverSearchItem? {
        val stockResults = results.filter { it.category == "stock" }
        if (stockResults.isEmpty()) return null

        return stockResults.firstOrNull { it.code.equals(query, ignoreCase = true) }
            ?: stockResults.firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?: stockResults.firstOrNull { it.name.contains(query, ignoreCase = true) }
            ?: stockResults.first()
    }

    private fun applyFilter() {
        val query = filterField.text.trim()
        val sourceItems = when (currentSource()) {
            SourceTab.CORE -> latestData[(coreCategoryCombo.selectedItem as? ResearchCategoryKey) ?: ResearchCategoryKey.MARKET].orEmpty()
            SourceTab.RANKING -> rankingData
            SourceTab.STOCK -> stockData
        }

        val filtered = if (query.isBlank()) {
            sourceItems
        } else {
            sourceItems.filter { article ->
                listOfNotNull(article.title, article.itemName, article.brokerName, article.category)
                    .any { it.contains(query, ignoreCase = true) }
            }
        }

        currentModel().replaceAll(filtered)
        if (filtered.isNotEmpty() && currentList().selectedIndex == -1) {
            currentList().selectedIndex = 0
        }
        if (filtered.isEmpty()) {
            updateDetail(null)
        }
    }

    private fun updateDetail(article: NaverResearchArticle?) {
        selectedArticle = article

        if (article == null) {
            detailTitleLabel.text = localizationService.text("리서치를 선택하세요", "Select research")
            detailMetaLabel.text = " "
            detailBodyPane.text = emptyHtmlBody(localizationService.text("표시할 리서치가 없습니다.", "Nothing to show"))
            openButton.isEnabled = false
            relatedStockButton.isEnabled = false
            return
        }

        detailTitleLabel.text = article.title
        detailMetaLabel.text = buildMetaText(article)
        detailBodyPane.text = buildHtmlBody(article)
        detailBodyPane.caretPosition = 0
        openButton.isEnabled = article.endUrl.isNotBlank()
        relatedStockButton.isEnabled = !article.itemCode.isNullOrBlank() || !article.itemName.isNullOrBlank()
    }

    private fun buildMetaText(article: NaverResearchArticle): String {
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

    private fun buildHtmlBody(article: NaverResearchArticle): String {
        val content = article.content.takeIf { it.isNotBlank() }
            ?: "<p>${localizationService.text("본문 미리보기가 없습니다.", "No preview available.")}</p>"

        return """
            <html>
            <body style="font-family: sans-serif; font-size: 12px; margin: 8px;">
                $content
            </body>
            </html>
        """.trimIndent()
    }

    private fun emptyHtmlBody(message: String): String {
        return """
            <html>
            <body style="font-family: sans-serif; font-size: 12px; margin: 8px; color: #888888;">
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

    private fun currentList(): JBList<NaverResearchArticle> {
        return when (currentSource()) {
            SourceTab.CORE -> coreList
            SourceTab.RANKING -> rankingList
            SourceTab.STOCK -> stockList
        }
    }

    private fun currentModel(): CollectionListModel<NaverResearchArticle> {
        return when (currentSource()) {
            SourceTab.CORE -> coreModel
            SourceTab.RANKING -> rankingModel
            SourceTab.STOCK -> stockModel
        }
    }

    private fun createResearchList(model: CollectionListModel<NaverResearchArticle>): JBList<NaverResearchArticle> {
        return JBList(model).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = SimpleListCellRenderer.create { label, value, _ ->
                label.border = JBUI.Borders.empty(8, 6)
                label.text = formatListText(value)
            }
        }
    }

    private fun formatListText(article: NaverResearchArticle?): String {
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

    private fun buildRankingMeta(ranking: NaverResearchRankingResponse, selectedRank: Int): String? {
        val item = ranking.ranking.getOrNull(selectedRank - 1) ?: return null
        val parts = listOfNotNull(
            "${selectedRank}위 ${item.itemName}",
            item.nowVal.takeIf { it.isNotBlank() }?.let { "현재가 $it" },
            item.changeRate.takeIf { it.isNotBlank() }?.let { "등락률 ${if (it.startsWith("-")) "" else "+"}$it%" },
            item.per.takeIf { it.isNotBlank() }?.let { "PER $it" },
            item.pbr.takeIf { it.isNotBlank() }?.let { "PBR $it" }
        )
        return parts.joinToString(" · ").ifBlank { null }
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
