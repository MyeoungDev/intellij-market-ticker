package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.github.myeoungdev.marketticker.application.model.news.TickerNewsSummaryViewData
import com.github.myeoungdev.marketticker.domain.model.news.TickerOverviewCard
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.NewsFacadeService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ListSelectionModel

/**
 * 주식 탭 하단에 노출되는 종목 뉴스 요약 패널입니다.
 */
class StockNewsSummaryPanel : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val newsFacadeService = service<NewsFacadeService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var requestJob: Job? = null

    private val titleLabel = JLabel(localizationService.text("선택 종목 뉴스", "Selected Stock News"))
    private val statusLabel = JLabel(localizationService.text("관심종목을 선택하면 뉴스를 표시합니다.", "Select a watchlist ticker to see news."))
    private val overviewTitleLabel = JLabel()
    private val overviewMetaPrimaryLabel = JLabel()
    private val overviewMetaSecondaryLabel = JLabel()
    private val overviewPrimaryMetricsLabel = JLabel()
    private val overviewSecondaryMetricsLabel = JLabel()
    private val overviewSummaryArea = JTextArea()
    private val overviewToggleButton = JButton()
    private val overviewSiteButton = JButton(localizationService.text("공식 사이트", "Official Site"))
    private val overviewPanel = JPanel(BorderLayout(0, 6))
    private val model = CollectionListModel<NewsArticle>()
    private val list = JBList(model)
    private var currentOverviewUrl: String? = null
    private var isOverviewExpanded: Boolean = false

    init {
        border = JBUI.Borders.empty(8)

        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size2D + 1f)
        statusLabel.foreground = JBColor.GRAY
        overviewTitleLabel.font = overviewTitleLabel.font.deriveFont(overviewTitleLabel.font.size2D + 1f)
        overviewMetaPrimaryLabel.foreground = JBColor.GRAY
        overviewMetaSecondaryLabel.foreground = JBColor.GRAY
        overviewPrimaryMetricsLabel.foreground = JBColor.foreground()
        overviewSecondaryMetricsLabel.foreground = JBColor.GRAY
        overviewMetaSecondaryLabel.isVisible = false
        overviewSecondaryMetricsLabel.isVisible = false
        overviewSummaryArea.isEditable = false
        overviewSummaryArea.isOpaque = false
        overviewSummaryArea.lineWrap = true
        overviewSummaryArea.wrapStyleWord = true
        overviewSummaryArea.rows = 3
        overviewSummaryArea.border = JBUI.Borders.empty()
        overviewSummaryArea.foreground = JBColor.foreground()
        overviewSummaryArea.font = overviewSummaryArea.font.deriveFont(12f)
        overviewSummaryArea.isVisible = false
        overviewToggleButton.isVisible = false
        overviewToggleButton.addActionListener {
            isOverviewExpanded = !isOverviewExpanded
            updateOverviewExpansion()
        }
        overviewSiteButton.isVisible = false
        overviewSiteButton.addActionListener {
            currentOverviewUrl?.let(BrowserUtil::browse)
        }

        overviewPanel.apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(8)
            )
            isVisible = false
            add(
                JPanel(BorderLayout(0, 6)).apply {
                    isOpaque = false
                    add(
                        JPanel().apply {
                            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                            isOpaque = false
                            add(overviewTitleLabel)
                            add(overviewMetaPrimaryLabel)
                            add(overviewMetaSecondaryLabel)
                            add(overviewPrimaryMetricsLabel)
                            add(overviewSecondaryMetricsLabel)
                        },
                        BorderLayout.CENTER
                    )
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                            isOpaque = false
                            add(overviewToggleButton)
                            add(overviewSiteButton)
                        },
                        BorderLayout.SOUTH
                    )
                },
                BorderLayout.NORTH
            )
            add(overviewSummaryArea, BorderLayout.CENTER)
        }

        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.visibleRowCount = 4
        list.cellRenderer = SimpleListCellRenderer.create { label, value, _ ->
            val article = value ?: return@create
            val meta = listOfNotNull(
                article.badgeLabel.takeIf { it.isNotBlank() },
                article.source.takeIf { it.isNotBlank() },
                article.publishedAt.takeIf { it.isNotBlank() }?.let(::formatDate)
            ).joinToString(" · ")
            label.border = JBUI.Borders.empty(6, 6)
            label.text = """
                <html>
                <div style="line-height:1.3;"><b>${escapeHtml(article.title)}</b></div>
                <div style="color:#8a8a8a; margin-top:3px;">${escapeHtml(meta)}</div>
                </html>
            """.trimIndent()
        }

        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    list.selectedValue?.url?.let(BrowserUtil::browse)
                }
            }
        })

        add(
            JPanel(BorderLayout(0, 8)).apply {
                isOpaque = false
                add(
                    JPanel(BorderLayout()).apply {
                        isOpaque = false
                        add(titleLabel, BorderLayout.NORTH)
                        add(statusLabel, BorderLayout.SOUTH)
                    },
                    BorderLayout.NORTH
                )
                add(overviewPanel, BorderLayout.CENTER)
            },
            BorderLayout.NORTH
        )
        add(
            JBScrollPane(list).apply {
                border = JBUI.Borders.emptyTop(8)
                horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            },
            BorderLayout.CENTER
        )
    }

    fun showTicker(ticker: Ticker) {
        requestJob?.cancel()
        titleLabel.text = localizationService.text("${ticker.name} 뉴스", "${ticker.name} News")
        statusLabel.text = localizationService.text("뉴스를 불러오는 중...", "Loading news...")
        hideOverview()
        model.replaceAll(
            listOf(
                NewsArticle(id = "loading", title = localizationService.text("뉴스를 불러오는 중...", "Loading news..."))
            )
        )

        requestJob = scope.launch {
            val viewData = newsFacadeService.loadTickerNewsSummary(ticker)
            withContext(Dispatchers.Main) {
                if (!isActive) return@withContext
                applyViewData(viewData)
            }
        }
    }

    override fun dispose() {
        requestJob?.cancel()
        scope.cancel()
    }

    private fun applyViewData(viewData: TickerNewsSummaryViewData) {
        titleLabel.text = localizationService.text(viewData.title, viewData.title)
        renderOverview(viewData.overviewCard)

        if (viewData.articles.isEmpty()) {
            model.replaceAll(
                listOf(
                    NewsArticle(id = "empty", title = localizationService.text("표시할 뉴스가 없습니다.", "No news available."))
                )
            )
            statusLabel.text = localizationService.text(viewData.statusMessage, viewData.statusMessage)
            return
        }

        model.replaceAll(viewData.articles)
        if (list.selectedIndex == -1) {
            list.selectedIndex = 0
        }
        statusLabel.text = localizationService.text(viewData.subtitle, viewData.subtitle)
    }

    private fun renderOverview(card: TickerOverviewCard?) {
        if (card == null) {
            hideOverview()
            return
        }

        overviewTitleLabel.text = card.title
        overviewMetaPrimaryLabel.text = card.metaPrimary
        overviewMetaSecondaryLabel.text = card.metaSecondary
        overviewPrimaryMetricsLabel.text = card.primaryMetrics
        overviewSecondaryMetricsLabel.text = card.secondaryMetrics
        overviewSummaryArea.text = card.summary
        currentOverviewUrl = card.siteUrl
        isOverviewExpanded = false
        overviewToggleButton.isVisible = card.summary.isNotBlank()
        overviewSiteButton.isVisible = !currentOverviewUrl.isNullOrBlank()
        updateOverviewExpansion()
        overviewPanel.isVisible = true
    }

    private fun hideOverview() {
        overviewPanel.isVisible = false
        overviewTitleLabel.text = ""
        overviewMetaPrimaryLabel.text = ""
        overviewMetaSecondaryLabel.text = ""
        overviewPrimaryMetricsLabel.text = ""
        overviewSecondaryMetricsLabel.text = ""
        overviewSummaryArea.text = ""
        overviewSummaryArea.isVisible = false
        currentOverviewUrl = null
        isOverviewExpanded = false
        overviewToggleButton.isVisible = false
        overviewSiteButton.isVisible = false
    }

    private fun formatDate(raw: String): String {
        return when {
            raw.length >= 12 && raw.all(Char::isDigit) ->
                "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"

            raw.length >= 10 -> raw.substring(0, 10)
            else -> raw
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private fun updateOverviewExpansion() {
        overviewSummaryArea.isVisible = isOverviewExpanded
        overviewMetaSecondaryLabel.isVisible = isOverviewExpanded && overviewMetaSecondaryLabel.text.isNotBlank()
        overviewSecondaryMetricsLabel.isVisible = isOverviewExpanded && overviewSecondaryMetricsLabel.text.isNotBlank()
        overviewToggleButton.text = localizationService.text(
            if (isOverviewExpanded) "접기" else "더보기",
            if (isOverviewExpanded) "Less" else "More"
        )
        overviewPanel.revalidate()
        overviewPanel.repaint()
    }
}
