package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.model.news.NewsHomeViewData
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.NewsFacadeService
import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

/**
 * 뉴스 탭입니다.
 *
 * 카테고리별 긴 스크롤 패널 대신 단일 selector 기반으로
 * 네이버 뉴스 섹션을 빠르게 전환하도록 구성합니다.
 */
class NewsView(
    private val project: Project
) : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val newsFacadeService = service<NewsFacadeService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val refreshButton = JButton(localizationService.text("새로고침", "Refresh"))
    private val openButton = JButton(localizationService.text("원문 열기", "Open article"))
    private val statusLabel = JLabel(localizationService.text("뉴스를 불러오는 중...", "Loading news..."))

    private val detailBadgeLabel = JLabel()
    private val detailTitleLabel = JLabel(localizationService.text("뉴스를 선택하세요", "Select a news item"))
    private val detailMetaLabel = JLabel()
    private val detailSummaryArea = JTextArea()
    private val detailLinkLabel = JLabel()

    private val newsTabs = JBTabsFactory.createTabs(project, this)
    private val categorySelector = JComboBox<String>()
    private val categoryKeys = mutableListOf<String>()
    private var selectedCategoryKey: String = "MAINNEWS"
    private var rebuildingCategories: Boolean = false
    private var categoryArticles: Map<String, List<NewsArticle>> = emptyMap()

    private val categoryListModel = DefaultListModel<NewsArticle>()
    private val categoryList = createNewsList(categoryListModel, CompactNewsRenderer())
    private val rankingListModel = DefaultListModel<NewsArticle>()
    private val rankingList = createNewsList(rankingListModel, RankingNewsRenderer())
    private var currentDetailArticle: NewsArticle? = null

    init {
        border = JBUI.Borders.empty(10)
        layout = BorderLayout(0, 10)

        add(buildToolbar(), BorderLayout.NORTH)
        add(buildCenterPanel(), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        bindList(categoryList)
        bindList(rankingList)

        renderDetail(null)
        loadNews(forceRefresh = false)
    }

    fun refreshNews() {
        loadNews(forceRefresh = true)
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun loadNews(forceRefresh: Boolean) {
        statusLabel.text = localizationService.text("뉴스를 불러오는 중...", "Loading news...")
        scope.launch {
            val homeData = newsFacadeService.loadNewsHome(forceRefresh = forceRefresh)
            withContext(Dispatchers.Main) {
                applyNewsHome(homeData)
            }
        }
    }

    private fun buildToolbar(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(
                JLabel(localizationService.text("뉴스", "News")).apply {
                    font = font.deriveFont(Font.BOLD, font.size2D + 1f)
                },
                BorderLayout.WEST
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(refreshButton)
                    add(openButton)
                },
                BorderLayout.EAST
            )
        }.also {
            refreshButton.addActionListener { refreshNews() }
            openButton.addActionListener { currentDetailArticle?.url?.let(BrowserUtil::browse) }
            openButton.isEnabled = false
        }
    }

    private fun buildCenterPanel(): JPanel {
        detailSummaryArea.isEditable = false
        detailSummaryArea.lineWrap = true
        detailSummaryArea.wrapStyleWord = true
        detailSummaryArea.background = JBColor.PanelBackground
        detailSummaryArea.foreground = JBColor.foreground()
        detailSummaryArea.font = detailSummaryArea.font.deriveFont(12f)
        detailSummaryArea.margin = Insets(5, 4, 5, 4)

        detailMetaLabel.foreground = JBColor.GRAY
        detailLinkLabel.foreground = JBColor.GRAY
        detailTitleLabel.font = detailTitleLabel.font.deriveFont(Font.BOLD, detailTitleLabel.font.size2D + 1f)
        detailBadgeLabel.border = JBUI.Borders.emptyBottom(4)

        val detailHeader = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            isOpaque = false
            add(detailBadgeLabel)
            add(detailTitleLabel)
            add(detailMetaLabel)
        }

        val detailPanel = JPanel(BorderLayout(0, 6)).apply {
            border = titledPanelBorder(localizationService.text("선택 뉴스", "Selected News"))
            add(detailHeader, BorderLayout.NORTH)
            add(JBScrollPane(detailSummaryArea), BorderLayout.CENTER)
            add(detailLinkLabel, BorderLayout.SOUTH)
            preferredSize = Dimension(0, 240)
        }

        newsTabs.addTabInfo(localizationService.text("뉴스 필터", "News Filter"), buildCategoryTab())
        newsTabs.addTabInfo(localizationService.text("많이 본 뉴스", "Most Viewed"), buildRankingTab())

        return JPanel(BorderLayout(0, 10)).apply {
            add(newsTabs.component, BorderLayout.CENTER)
            add(detailPanel, BorderLayout.SOUTH)
        }
    }

    private fun com.intellij.ui.tabs.JBTabs.addTabInfo(title: String, component: JComponent) {
        addTab(TabInfo(component).setText(title))
    }

    private fun buildCategoryTab(): JPanel {
        categorySelector.maximumRowCount = 12
        categorySelector.font = categorySelector.font.deriveFont(categorySelector.font.size2D - 1f)
        categorySelector.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                label.border = JBUI.Borders.empty(1, 4)
                return label
            }
        }
        categorySelector.addActionListener {
            if (rebuildingCategories) return@addActionListener
            val selectedIndex = categorySelector.selectedIndex
            if (selectedIndex >= 0 && selectedIndex < categoryKeys.size) {
                val key = categoryKeys[selectedIndex]
                if (selectedCategoryKey != key) {
                    selectedCategoryKey = key
                    applyCategory(key)
                }
            }
        }

        return JPanel(BorderLayout(0, 8)).apply {
            add(
                wrapSection(
                    localizationService.text("카테고리 선택", "Select Category"),
                    JPanel(BorderLayout()).apply {
                        isOpaque = false
                        add(categorySelector, BorderLayout.CENTER)
                    }
                ),
                BorderLayout.NORTH
            )
            add(
                wrapSection(
                    localizationService.text("기사 목록", "Articles"),
                    JBScrollPane(categoryList)
                ),
                BorderLayout.CENTER
            )
        }
    }

    private fun buildRankingTab(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(
                wrapSection(
                    localizationService.text("많이 본 뉴스", "Most Viewed"),
                    JBScrollPane(rankingList)
                ),
                BorderLayout.CENTER
            )
        }
    }

    private fun applyNewsHome(homeData: NewsHomeViewData) {
        rankingListModel.clear()
        homeData.mostViewed.forEach(rankingListModel::addElement)

        categoryArticles = buildCategoryMap(homeData)
        rebuildCategories()
        applyCategory(selectedCategoryKey)

        val firstSelectable = categoryListModel.firstOrNull() ?: rankingListModel.firstOrNull()
        if (firstSelectable != null) {
            renderDetail(firstSelectable)
        }

        statusLabel.text = localizationService.text(
            "${displayCategory(selectedCategoryKey)} ${categoryListModel.size()}건 · 많이 본 뉴스 ${rankingListModel.size()}건",
            "${displayCategory(selectedCategoryKey)} ${categoryListModel.size()} items · most viewed ${rankingListModel.size()}"
        )
        revalidate()
        repaint()
    }

    private fun buildCategoryMap(homeData: NewsHomeViewData): Map<String, List<NewsArticle>> {
        val map = linkedMapOf<String, List<NewsArticle>>()
        homeData.headlines.headlines.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                map[key] = value
            }
        }
        homeData.headlines.focusSections.forEachIndexed { index, section ->
            if (section.articles.isNotEmpty()) {
                map["FOCUS_$index"] = section.articles
            }
        }
        if (homeData.headlines.worldNews.isNotEmpty()) {
            map["OVERSEAS"] = homeData.headlines.worldNews
        }
        if (homeData.headlines.moneyStories.isNotEmpty()) {
            map["MONEY"] = homeData.headlines.moneyStories
        }
        return map
    }

    private fun rebuildCategories() {
        rebuildingCategories = true
        try {
            categorySelector.removeAllItems()
            categoryKeys.clear()

            categoryArticles.keys.forEach { key ->
                categoryKeys += key
                categorySelector.addItem(displayCategory(key))
            }

            if (!categoryKeys.contains(selectedCategoryKey)) {
                selectedCategoryKey = categoryKeys.firstOrNull() ?: "MAINNEWS"
            }

            val selectedIndex = categoryKeys.indexOf(selectedCategoryKey)
            if (selectedIndex >= 0) {
                categorySelector.selectedIndex = selectedIndex
            }
        } finally {
            rebuildingCategories = false
        }
    }

    private fun applyCategory(categoryKey: String) {
        categoryListModel.clear()
        categoryArticles[categoryKey].orEmpty().forEach(categoryListModel::addElement)
        if (!categoryListModel.isEmpty) {
            categoryList.selectedIndex = 0
        } else {
            renderDetail(null)
        }
    }

    private fun displayCategory(categoryKey: String): String {
        return when {
            categoryKey == "FLASHNEWS" -> localizationService.text("속보", "Flash")
            categoryKey == "MAINNEWS" -> localizationService.text("주요 뉴스", "Main News")
            categoryKey == "WORLDNEWS" || categoryKey == "OVERSEAS" ->
                localizationService.text("해외증시", "Overseas")
            categoryKey == "MONEY" -> localizationService.text("머니스토리", "Money Story")
            categoryKey.startsWith("FOCUS_") -> {
                categoryArticles[categoryKey]?.firstOrNull()?.sectionLabel?.takeIf { it.isNotBlank() }
                    ?: localizationService.text("카테고리", "Category")
            }
            else -> categoryKey
        }
    }

    private fun renderDetail(article: NewsArticle?) {
        currentDetailArticle = article
        if (article == null) {
            detailBadgeLabel.text = ""
            detailTitleLabel.text = localizationService.text("뉴스를 선택하세요", "Select a news item")
            detailMetaLabel.text = localizationService.text(
                "카테고리를 고른 뒤 기사를 선택하면 상세 내용을 빠르게 확인할 수 있습니다.",
                "Choose a category and select an article to inspect the details quickly."
            )
            detailSummaryArea.text = localizationService.text(
                "긴 스크롤 대신 selector 기반으로 섹션을 바꿔가며 읽도록 정리했습니다.",
                "The tab now uses a selector instead of long stacked scrolling sections."
            )
            detailLinkLabel.text = ""
            openButton.isEnabled = false
            return
        }

        detailBadgeLabel.text = buildBadgeText(article.badgeLabel, article.badgeColor)
        detailTitleLabel.text = article.title
        detailMetaLabel.text = listOfNotNull(
            article.sectionLabel.takeIf { it.isNotBlank() },
            article.source.takeIf { it.isNotBlank() },
            article.publishedAt.takeIf { it.isNotBlank() }
        ).joinToString("  |  ")
        detailSummaryArea.text = article.summary.takeIf { it.isNotBlank() }
            ?: localizationService.text("요약 정보가 없습니다.", "No summary available.")
        detailSummaryArea.caretPosition = 0
        detailLinkLabel.text = article.url?.let {
            localizationService.text("원문: ", "Source: ") + it
        } ?: localizationService.text("플러그인 내부 요약 항목입니다.", "This is an internal summary item.")
        openButton.isEnabled = !article.url.isNullOrBlank()
    }

    private fun createNewsList(
        model: DefaultListModel<NewsArticle>,
        renderer: DefaultListCellRenderer
    ): JBList<NewsArticle> {
        return JBList(model).apply {
            cellRenderer = renderer
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            fixedCellHeight = -1
            visibleRowCount = 8
        }
    }

    private fun bindList(list: JBList<NewsArticle>) {
        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                renderDetail(list.selectedValue)
            }
        }
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val article = list.selectedValue ?: return
                if (e.clickCount == 2) {
                    article.url?.let(BrowserUtil::browse)
                }
            }
        })
    }

    private fun wrapSection(title: String, component: Component): JPanel {
        return JPanel(BorderLayout()).apply {
            border = titledPanelBorder(title)
            background = JBColor.PanelBackground
            add(component, BorderLayout.CENTER)
        }
    }

    private fun titledPanelBorder(title: String) = BorderFactory.createCompoundBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0x3A404A, 0x3A404A)),
            BorderFactory.createTitledBorder(title)
        ),
        JBUI.Borders.empty(8)
    )

    private fun buildBadgeText(label: String?, color: String?): String {
        if (label.isNullOrBlank()) return ""
        return "<html><span style='font-weight:700; color:${badgeColor(color)};'>${escapeHtml(label)}</span></html>"
    }

    private fun badgeColor(color: String?): String = when (color?.lowercase()) {
        "red" -> "#FF6B6B"
        "green" -> "#23B26D"
        "gray" -> "#9AA0A6"
        "blue" -> "#5B8CFF"
        else -> "#AAB2C0"
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private fun DefaultListModel<NewsArticle>.firstOrNull(): NewsArticle? {
        return if (isEmpty) null else getElementAt(0)
    }

    private inner class CompactNewsRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val article = value as? NewsArticle ?: return label
            val metaColor = if (isSelected) "#DCE8FF" else "#89909A"
            val badgeHtml = article.badgeLabel.takeIf { it.isNotBlank() }?.let {
                "<span style='color:${badgeColor(article.badgeColor)}; font-weight:700;'>${escapeHtml(it)}</span> "
            } ?: ""
            val summaryHtml = article.summary.takeIf { it.isNotBlank() }?.let {
                "<div style='margin-top:5px; color:$metaColor; line-height:1.3; font-size:11px;'>${escapeHtml(trimSummary(it))}</div>"
            }.orEmpty()
            label.verticalAlignment = SwingConstants.TOP
            label.border = JBUI.Borders.empty(8, 8)
            label.text = """
                <html>
                  <div style='width:420px;'>
                    <div style='line-height:1.34; font-weight:700; font-size:11px;'>$badgeHtml${escapeHtml(article.title)}</div>
                    <div style='margin-top:4px; color:$metaColor; font-size:11px;'>${escapeHtml(article.source.ifBlank { "-" })} · ${escapeHtml(article.publishedAt.ifBlank { "-" })}</div>
                    $summaryHtml
                  </div>
                </html>
            """.trimIndent()
            return label
        }
    }

    private inner class RankingNewsRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val article = value as? NewsArticle ?: return label
            val rank = article.ranking ?: (index + 1).toString()
            val metaColor = if (isSelected) "#DCE8FF" else "#89909A"

            label.verticalAlignment = SwingConstants.TOP
            label.border = JBUI.Borders.empty(9, 8)
            label.text = """
                <html>
                  <div style='width:420px;'>
                    <table cellspacing='0' cellpadding='0'>
                      <tr>
                        <td style='width:28px; color:#5B8CFF; font-weight:700; vertical-align:top;'>$rank</td>
                        <td>
                            <div style='line-height:1.34; font-weight:700; font-size:11px;'>${escapeHtml(article.title)}</div>
                            <div style='margin-top:4px; color:$metaColor; font-size:11px;'>${escapeHtml(article.source.ifBlank { "-" })} · ${escapeHtml(article.publishedAt.ifBlank { "-" })}</div>
                            <div style='margin-top:5px; color:$metaColor; line-height:1.3; font-size:11px;'>${escapeHtml(trimSummary(article.summary.ifBlank { localizationService.text("요약 정보 없음", "No summary") }, 120))}</div>
                        </td>
                      </tr>
                    </table>
                </div>
                </html>
            """.trimIndent()
            return label
        }
    }

    private fun trimSummary(text: String, maxLength: Int = 100): String {
        val normalized = text.replace("\n", " ").trim()
        if (normalized.length <= maxLength) return normalized
        return normalized.take(maxLength - 1).trimEnd() + "…"
    }
}
