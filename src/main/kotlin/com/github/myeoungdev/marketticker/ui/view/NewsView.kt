package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.github.myeoungdev.marketticker.application.model.news.NewsHomeViewData
import com.github.myeoungdev.marketticker.application.service.NewsFacadeService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
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
import javax.swing.Box
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

/**
 * 뉴스 탭입니다.
 *
 * 툴윈도우 폭이 좁아도 카테고리별 원본 뉴스 리스트를 빠르게 훑을 수 있도록
 * `헤드라인 / 많이 본 뉴스` 2개 정보 축으로 구성합니다.
 */
class NewsView : JPanel(BorderLayout()) {

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

    private val newsTabs = JTabbedPane()
    private val headlineCategoryPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
    private val headlineCategoryButtons = linkedMapOf<String, JButton>()
    private var selectedHeadlineCategoryKey: String = "MAINNEWS"
    private var headlineCategoryArticles: Map<String, List<NewsArticle>> = emptyMap()

    private val headlineListModel = DefaultListModel<NewsArticle>()
    private val headlineList = createNewsList(headlineListModel, CompactNewsRenderer())
    private val overseasListModel = DefaultListModel<NewsArticle>()
    private val overseasList = createNewsList(overseasListModel, CompactNewsRenderer())
    private val moneyStoryListModel = DefaultListModel<NewsArticle>()
    private val moneyStoryList = createNewsList(moneyStoryListModel, CompactNewsRenderer())
    private val focusContainer = JPanel()

    private val rankingListModel = DefaultListModel<NewsArticle>()
    private val rankingList = createNewsList(rankingListModel, RankingNewsRenderer())

    init {
        border = JBUI.Borders.empty(10)
        layout = BorderLayout(0, 10)

        add(buildToolbar(), BorderLayout.NORTH)
        add(buildCenterPanel(), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        bindList(headlineList)
        bindList(overseasList)
        bindList(moneyStoryList)
        bindList(rankingList)

        renderDetail(null)
        loadNews(forceRefresh = false)
    }

    /**
     * 뉴스 데이터를 새로 불러와 화면에 반영합니다.
     */
    fun refreshNews() {
        loadNews(forceRefresh = true)
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

    override fun removeNotify() {
        super.removeNotify()
        scope.cancel()
    }

    private fun buildToolbar(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(
                JLabel(localizationService.text("뉴스", "News")).apply {
                    font = font.deriveFont(Font.BOLD, font.size2D + 2f)
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
            openButton.addActionListener { selectedArticle()?.url?.let(BrowserUtil::browse) }
            openButton.isEnabled = false
        }
    }

    private fun buildCenterPanel(): JPanel {
        detailSummaryArea.isEditable = false
        detailSummaryArea.lineWrap = true
        detailSummaryArea.wrapStyleWord = true
        detailSummaryArea.background = JBColor.PanelBackground
        detailSummaryArea.foreground = JBColor.foreground()
        detailSummaryArea.font = detailSummaryArea.font.deriveFont(13f)

        detailMetaLabel.foreground = JBColor.GRAY
        detailLinkLabel.foreground = JBColor.GRAY

        val detailHeader = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
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
            preferredSize = Dimension(0, 220)
        }

        newsTabs.addTab(localizationService.text("헤드라인", "Headlines"), buildHeadlinesTab())
        newsTabs.addTab(localizationService.text("많이 본 뉴스", "Most Viewed"), buildRankingTab())

        return JPanel(BorderLayout(0, 10)).apply {
            add(newsTabs, BorderLayout.CENTER)
            add(detailPanel, BorderLayout.SOUTH)
        }
    }

    private fun buildHeadlinesTab(): JBScrollPane {
        focusContainer.layout = BoxLayout(focusContainer, BoxLayout.Y_AXIS)
        focusContainer.isOpaque = false
        headlineCategoryPanel.isOpaque = false

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(wrapSection(localizationService.text("뉴스 카테고리", "News Category"), headlineCategoryPanel))
            add(spacer())
            add(wrapSection(localizationService.text("주요 뉴스", "Headlines"), JBScrollPane(headlineList).fixedHeight(260)))
            add(spacer())
            add(wrapSection(localizationService.text("포커스", "Focus"), focusContainer))
            add(spacer())
            add(wrapSection(localizationService.text("해외 뉴스", "Overseas"), JBScrollPane(overseasList).fixedHeight(170)))
            add(spacer())
            add(wrapSection(localizationService.text("머니스토리", "Money Story"), JBScrollPane(moneyStoryList).fixedHeight(170)))
        }

        return JBScrollPane(content).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
    }

    private fun buildRankingTab(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(wrapSection(localizationService.text("많이 본 뉴스", "Most Viewed"), JBScrollPane(rankingList)), BorderLayout.CENTER)
        }
    }

    private fun applyNewsHome(homeData: NewsHomeViewData) {
        headlineListModel.clear()
        overseasListModel.clear()
        moneyStoryListModel.clear()
        rankingListModel.clear()
        focusContainer.removeAll()

        headlineCategoryArticles = homeData.headlines.headlines

        rebuildHeadlineCategories()
        applyHeadlineCategory(selectedHeadlineCategoryKey)

        homeData.headlines.worldNews.forEach(overseasListModel::addElement)
        homeData.headlines.moneyStories.forEach(moneyStoryListModel::addElement)
        homeData.mostViewed.forEach(rankingListModel::addElement)

        homeData.headlines.focusSections.forEach { section ->
            focusContainer.add(
                wrapSection(
                    section.title,
                    JBScrollPane(
                        createNewsList(
                            DefaultListModel<NewsArticle>().apply {
                                section.articles.take(4).forEach(::addElement)
                            },
                            CompactNewsRenderer()
                        ).also { bindList(it) }
                    ).fixedHeight(140)
                )
            )
            focusContainer.add(spacer())
        }

        val firstSelectable = headlineListModel.firstOrNull()
            ?: rankingListModel.firstOrNull()
        renderDetail(firstSelectable)

        statusLabel.text = localizationService.text(
            "선택 카테고리 ${headlineListModel.size()}건, 많이 본 뉴스 ${rankingListModel.size()}건",
            "Selected category ${headlineListModel.size()}, most viewed ${rankingListModel.size()}"
        )
        openButton.isEnabled = !firstSelectable?.url.isNullOrBlank()

        revalidate()
        repaint()
    }

    private fun rebuildHeadlineCategories() {
        headlineCategoryPanel.removeAll()
        headlineCategoryButtons.clear()

        val categoryItems = listOf(
            "FLASHNEWS" to localizationService.text("속보", "Flash"),
            "MAINNEWS" to localizationService.text("주요", "Main"),
            "WORLDNEWS" to localizationService.text("해외뉴스", "World News")
        )

        categoryItems.forEach { (key, label) ->
            val button = JButton(label).apply {
                margin = JBUI.insets(4, 8)
                isFocusPainted = false
                addActionListener {
                    selectedHeadlineCategoryKey = key
                    applyHeadlineCategory(key)
                }
            }
            headlineCategoryButtons[key] = button
            headlineCategoryPanel.add(button)
        }

        headlineCategoryPanel.add(Box.createHorizontalStrut(4))
        updateHeadlineCategoryStyles()
        headlineCategoryPanel.revalidate()
        headlineCategoryPanel.repaint()
    }

    private fun applyHeadlineCategory(categoryKey: String) {
        headlineListModel.clear()
        headlineCategoryArticles[categoryKey].orEmpty().take(18).forEach(headlineListModel::addElement)
        if (!headlineListModel.isEmpty) {
            headlineList.selectedIndex = 0
        }
        updateHeadlineCategoryStyles()
    }

    private fun updateHeadlineCategoryStyles() {
        headlineCategoryButtons.forEach { (key, button) ->
            val selected = key == selectedHeadlineCategoryKey
            button.background = if (selected) JBColor(0x3559B7, 0x3559B7) else JBColor(0x3A3D42, 0x3A3D42)
            button.foreground = if (selected) JBColor.WHITE else JBColor(0xC8CDD5, 0xC8CDD5)
            button.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        }
    }

    private fun renderDetail(article: NewsArticle?) {
        if (article == null) {
            detailBadgeLabel.text = ""
            detailTitleLabel.text = localizationService.text("뉴스를 선택하세요", "Select a news item")
            detailMetaLabel.text = localizationService.text(
                "헤드라인과 많이 본 뉴스에서 중요한 정보를 빠르게 확인할 수 있습니다.",
                "Use headlines and most viewed news to scan the market quickly."
            )
            detailSummaryArea.text = localizationService.text(
                "상단은 맥락을 읽는 공간이 아니라, 선택한 항목의 핵심만 빠르게 확인하는 공간으로 유지했습니다.",
                "This area is optimized for scanning only the key context of the selected item."
            )
            detailLinkLabel.text = ""
            openButton.isEnabled = false
            return
        }

        detailBadgeLabel.text = buildBadgeText(article.badgeLabel, article.badgeColor)
        detailTitleLabel.text = article.title
        detailMetaLabel.text = listOfNotNull(article.source.takeIf { it.isNotBlank() }, article.publishedAt.takeIf { it.isNotBlank() }).joinToString("  |  ")
        detailSummaryArea.text = article.summary.takeIf { it.isNotBlank() }
            ?: localizationService.text("요약 정보가 없습니다.", "No summary available.")
        detailSummaryArea.caretPosition = 0
        detailLinkLabel.text = article.url?.let {
            localizationService.text("원문: ", "Source: ") + it
        } ?: localizationService.text("플러그인 내부 요약 항목입니다.", "This is an internal summary item.")
        openButton.isEnabled = !article.url.isNullOrBlank()
    }

    private fun selectedArticle(): NewsArticle? {
        return when (newsTabs.selectedIndex) {
            0 -> headlineList.selectedValue ?: overseasList.selectedValue ?: moneyStoryList.selectedValue
            1 -> rankingList.selectedValue
            else -> null
        }
    }

    private fun createNewsList(
        model: DefaultListModel<NewsArticle>,
        renderer: DefaultListCellRenderer
    ): JBList<NewsArticle> {
        return JBList(model).apply {
            cellRenderer = renderer
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            fixedCellHeight = -1
            visibleRowCount = 6
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
            add(component, BorderLayout.CENTER)
        }
    }

    private fun titledPanelBorder(title: String) = BorderFactory.createCompoundBorder(
        BorderFactory.createTitledBorder(title),
        JBUI.Borders.empty(8)
    )

    private fun spacer(): JPanel {
        return JPanel().apply {
            isOpaque = false
            preferredSize = Dimension(0, 8)
            maximumSize = Dimension(Int.MAX_VALUE, 8)
        }
    }

    private fun buildBadge(label: String?, color: String?): JLabel {
        return JLabel(buildBadgeText(label, color)).apply {
            border = JBUI.Borders.emptyBottom(6)
        }
    }

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

    private fun JBScrollPane.fixedHeight(height: Int): JBScrollPane {
        preferredSize = Dimension(0, height)
        minimumSize = Dimension(0, height)
        return this
    }

    private fun DefaultListModel<NewsArticle>.firstOrNull(): NewsArticle? {
        return if (isEmpty) null else getElementAt(0)
    }

    /**
     * 일반 기사 목록 렌더러입니다.
     */
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
            label.verticalAlignment = SwingConstants.TOP
            label.border = JBUI.Borders.empty(8, 6)
            label.text = """
                <html>
                  <div style='width:280px;'>
                    <div style='line-height:1.35; font-weight:500; font-size:12px;'>$badgeHtml${escapeHtml(article.title)}</div>
                    <div style='margin-top:5px; color:$metaColor;'>${escapeHtml(article.source.ifBlank { "-" })} · ${escapeHtml(article.publishedAt.ifBlank { "-" })}</div>
                  </div>
                </html>
            """.trimIndent()
            return label
        }
    }

    /**
     * 랭킹 뉴스 렌더러입니다.
     */
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
            label.border = JBUI.Borders.empty(10, 6)
            label.text = """
                <html>
                  <div style='width:280px;'>
                    <table cellspacing='0' cellpadding='0'>
                      <tr>
                        <td style='width:28px; color:#5B8CFF; font-weight:700; vertical-align:top;'>$rank</td>
                        <td>
                            <div style='line-height:1.35;'>${escapeHtml(article.title)}</div>
                            <div style='margin-top:5px; color:$metaColor;'>${escapeHtml(article.source.ifBlank { "-" })} · ${escapeHtml(article.publishedAt.ifBlank { "-" })}</div>
                            <div style='margin-top:5px; color:$metaColor;'>${escapeHtml(article.summary.ifBlank { localizationService.text("요약 정보 없음", "No summary") })}</div>
                        </td>
                      </tr>
                    </table>
                </div>
                </html>
            """.trimIndent()
            return label
        }
    }
}
