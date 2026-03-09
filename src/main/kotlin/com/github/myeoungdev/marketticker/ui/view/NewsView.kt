package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverClient
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverNewsArticle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
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
import java.time.LocalDate
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

/**
 * 뉴스 조회 탭입니다.
 *
 * 좁은 툴윈도우 폭에서도 읽기 쉽게 단일 컬럼 레이아웃을 사용합니다.
 */
class NewsView : JPanel(BorderLayout()) {

    private val localizationService = service<LocalizationService>()
    private val naverClient = NaverClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val categoryCombo = ComboBox(DefaultComboBoxModel(NewsCategory.values()))
    private val refreshButton = JButton(localizationService.text("새로고침", "Refresh"))
    private val openButton = JButton(localizationService.text("원문 열기", "Open article"))
    private val listModel = DefaultListModel<NaverNewsArticle>()
    private val newsList = JBList(listModel)
    private val statusLabel = JLabel(localizationService.text("뉴스를 불러오는 중...", "Loading news..."))

    private val detailTitleLabel = JLabel(localizationService.text("기사를 선택하세요", "Select an article"))
    private val detailMetaLabel = JLabel()
    private val detailSummaryArea = JTextArea()
    private val detailLinkLabel = JLabel()

    init {
        border = JBUI.Borders.empty(10)

        val toolbar = JPanel(BorderLayout(8, 0)).apply {
            border = JBUI.Borders.emptyBottom(10)
            add(categoryCombo, BorderLayout.WEST)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                    isOpaque = false
                    add(refreshButton)
                    add(openButton)
                },
                BorderLayout.EAST
            )
        }

        detailTitleLabel.font = detailTitleLabel.font.deriveFont(detailTitleLabel.font.size2D + 2f)
        detailMetaLabel.foreground = JBColor.GRAY
        detailLinkLabel.foreground = JBColor.GRAY

        detailSummaryArea.isEditable = false
        detailSummaryArea.lineWrap = true
        detailSummaryArea.wrapStyleWord = true
        detailSummaryArea.background = JBColor.PanelBackground
        detailSummaryArea.foreground = JBColor.foreground()
        detailSummaryArea.font = detailSummaryArea.font.deriveFont(14f)
        detailSummaryArea.border = JBUI.Borders.empty(6, 0, 0, 0)

        val detailHeader = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(detailTitleLabel)
            add(detailMetaLabel)
        }

        val detailPanel = JPanel(BorderLayout()).apply {
            border = titledPanelBorder(localizationService.text("선택 기사", "Selected Article"))
            add(detailHeader, BorderLayout.NORTH)
            add(JBScrollPane(detailSummaryArea), BorderLayout.CENTER)
            add(detailLinkLabel, BorderLayout.SOUTH)
            preferredSize = Dimension(0, 210)
            minimumSize = Dimension(0, 180)
        }

        newsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        newsList.cellRenderer = NewsListRenderer()
        newsList.fixedCellHeight = -1

        val listPanel = JPanel(BorderLayout()).apply {
            border = titledPanelBorder(localizationService.text("뉴스 목록", "News Feed"))
            add(JBScrollPane(newsList), BorderLayout.CENTER)
        }

        add(toolbar, BorderLayout.NORTH)
        add(
            JPanel(BorderLayout(0, 10)).apply {
                add(detailPanel, BorderLayout.NORTH)
                add(listPanel, BorderLayout.CENTER)
            },
            BorderLayout.CENTER
        )
        add(statusLabel, BorderLayout.SOUTH)

        refreshButton.addActionListener { refreshNews() }
        openButton.addActionListener {
            newsList.selectedValue?.articleUrl()?.let(BrowserUtil::browse)
        }
        categoryCombo.addActionListener { refreshNews() }
        newsList.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            renderArticleDetail(newsList.selectedValue)
        }
        newsList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount != 2) return
                newsList.selectedValue?.articleUrl()?.let(BrowserUtil::browse)
            }
        })

        renderArticleDetail(null)
        refreshNews()
    }

    private fun refreshNews() {
        val selected = categoryCombo.selectedItem as? NewsCategory ?: NewsCategory.FLASHNEWS
        statusLabel.text = localizationService.text("뉴스를 불러오는 중...", "Loading news...")

        scope.launch {
            val articles = if (selected == NewsCategory.RANKING) {
                naverClient.fetchRankingNews(limit = 20)
            } else {
                naverClient.fetchNewsList(
                    category = selected.apiValue,
                    page = 1,
                    pageSize = 20,
                    date = LocalDate.now()
                )
            }

            withContext(Dispatchers.Main) {
                listModel.clear()
                articles.forEach(listModel::addElement)
                statusLabel.text = if (articles.isEmpty()) {
                    localizationService.text("표시할 뉴스가 없습니다.", "No news to display.")
                } else {
                    localizationService.text("${articles.size}건 뉴스", "${articles.size} news items")
                }

                if (articles.isNotEmpty()) {
                    newsList.selectedIndex = 0
                } else {
                    renderArticleDetail(null)
                }
            }
        }
    }

    private fun renderArticleDetail(article: NaverNewsArticle?) {
        if (article == null) {
            detailTitleLabel.text = localizationService.text("기사를 선택하세요", "Select an article")
            detailMetaLabel.text = localizationService.text(
                "아래 목록에서 뉴스를 선택하면 요약이 표시됩니다.",
                "Select a news item below to view its summary."
            )
            detailSummaryArea.text = localizationService.text(
                "좁은 패널에서도 읽기 쉽도록 선택 기사 요약만 상단에 고정했습니다.",
                "The selected article summary is pinned above for narrow tool windows."
            )
            detailLinkLabel.text = ""
            openButton.isEnabled = false
            return
        }

        detailTitleLabel.text = article.title
        detailMetaLabel.text = "${article.officeHname ?: "-"}  |  ${article.datetime ?: "-"}"
        detailSummaryArea.text = article.subcontent ?: localizationService.text("요약 정보가 없습니다.", "No summary provided.")
        detailSummaryArea.caretPosition = 0
        detailLinkLabel.text = localizationService.text("원문: ", "Source: ") + (article.articleUrl() ?: "")
        openButton.isEnabled = !article.articleUrl().isNullOrBlank()
    }

    private fun titledPanelBorder(title: String) = BorderFactory.createCompoundBorder(
        BorderFactory.createTitledBorder(title),
        JBUI.Borders.empty(8)
    )

    override fun removeNotify() {
        super.removeNotify()
        scope.cancel()
    }

    private inner class NewsListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val article = value as? NaverNewsArticle
            if (article == null) {
                component.text = ""
                return component
            }

            component.border = JBUI.Borders.empty(8, 6)
            component.verticalAlignment = SwingConstants.TOP
            component.text = buildListCellHtml(
                article.title,
                article.officeHname ?: "-",
                article.datetime ?: "-",
                article.subcontent,
                isSelected
            )
            return component
        }
    }

    private fun buildListCellHtml(
        title: String,
        press: String,
        dt: String,
        summary: String?,
        isSelected: Boolean
    ): String {
        val metaColor = if (isSelected) "#DCE8FF" else "#7A7F87"
        val preview = escapeHtml(summary ?: "").take(88)
        val previewHtml = if (preview.isNotBlank()) {
            "<div style='margin-top:6px; color:#8C9198; line-height:1.3;'>$preview...</div>"
        } else {
            ""
        }

        return """
            <html>
              <div style='width:320px;'>
                <div style='font-weight:700; line-height:1.35;'>${escapeHtml(title)}</div>
                <div style='margin-top:6px; color:$metaColor;'>${escapeHtml(press)} · ${escapeHtml(dt)}</div>
                $previewHtml
              </div>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    enum class NewsCategory(val apiValue: String, private val ko: String, private val en: String) {
        FLASHNEWS("FLASHNEWS", "속보", "Flash"),
        MAINNEWS("MAINNEWS", "주요뉴스", "Main"),
        RANKING("RANKING", "랭킹", "Ranking");

        override fun toString(): String {
            val localizationService = ApplicationManager.getApplication()?.getService(LocalizationService::class.java)
            return localizationService?.text(ko, en) ?: ko
        }
    }
}
