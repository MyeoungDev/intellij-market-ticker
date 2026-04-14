package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.model.news.TickerNewsSummaryViewData
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.NewsFacadeService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.news.TickerOverviewCard
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
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
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.OverlayLayout
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * 주식 탭 하단에 노출되는 종목 개요 패널입니다.
 */
class StockOverviewPanel : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val newsFacadeService = service<NewsFacadeService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var requestJob: Job? = null

    private val titleLabel = JLabel(localizationService.text("선택 종목 개요", "Selected Stock Overview"))
    private val statusLabel = JLabel(localizationService.text("관심종목을 선택하면 개요를 표시합니다.", "Select a watchlist ticker to see overview."))
    private val overviewTitleLabel = JLabel()
    private val overviewMetaPrimaryLabel = JLabel()
    private val overviewMetaSecondaryLabel = JLabel()
    private val metricTitleLabel = JLabel(localizationService.text("핵심 지표", "Key Metrics"))
    private val overviewMetricWrap = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
    private val summaryTitleLabel = JLabel(localizationService.text("요약", "Summary"))
    private val overviewSummaryArea = JTextArea()
    private val overviewSiteButton = JButton(localizationService.text("공식 사이트", "Official Site"))
    private val contentPanel = JPanel(BorderLayout(0, 10))
    private val emptyStatePanel = JPanel(BorderLayout())
    private val bodyPanel = JPanel()
    private var currentOverviewUrl: String? = null

    init {
        border = JBUI.Borders.empty(8)
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size2D + 1f)
        statusLabel.foreground = JBColor.GRAY
        overviewTitleLabel.font = overviewTitleLabel.font.deriveFont(Font.BOLD, overviewTitleLabel.font.size2D + 2f)
        overviewMetaPrimaryLabel.foreground = JBColor.GRAY
        overviewMetaSecondaryLabel.foreground = JBColor.GRAY
        metricTitleLabel.font = metricTitleLabel.font.deriveFont(Font.BOLD)
        summaryTitleLabel.font = summaryTitleLabel.font.deriveFont(Font.BOLD)
        overviewMetricWrap.isOpaque = false
        overviewSummaryArea.isEditable = false
        overviewSummaryArea.isOpaque = true
        overviewSummaryArea.lineWrap = true
        overviewSummaryArea.wrapStyleWord = true
        overviewSummaryArea.rows = 6
        overviewSummaryArea.margin = JBUI.insets(8)
        overviewSummaryArea.border = JBUI.Borders.empty()
        overviewSummaryArea.background = JBColor(Color(250, 251, 252), Color(43, 46, 51))
        overviewSummaryArea.foreground = JBColor.foreground()
        overviewSummaryArea.font = overviewSummaryArea.font.deriveFont(12f)
        overviewSiteButton.isVisible = false
        overviewSiteButton.addActionListener { currentOverviewUrl?.let(BrowserUtil::browse) }
        preferredSize = Dimension(0, 260)
        minimumSize = Dimension(0, 200)

        contentPanel.isOpaque = false
        contentPanel.add(buildHeaderPanel(), BorderLayout.NORTH)
        bodyPanel.layout = OverlayLayout(bodyPanel)
        bodyPanel.isOpaque = false
        bodyPanel.add(contentPanel)
        bodyPanel.add(emptyStatePanel)
        add(bodyPanel, BorderLayout.CENTER)
        hideOverview()
    }

    fun showTicker(ticker: Ticker) {
        requestJob?.cancel()
        titleLabel.text = localizationService.text("${ticker.name} 개요", "${ticker.name} Overview")
        statusLabel.text = localizationService.text("개요를 불러오는 중...", "Loading overview...")
        showLoadingState()

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
        renderOverview(viewData.overviewCard)
        statusLabel.text = ""
    }

    private fun renderOverview(card: TickerOverviewCard?) {
        if (card == null) {
            hideOverview()
            return
        }

        overviewTitleLabel.text = card.title
        overviewMetaPrimaryLabel.text = card.metaPrimary
        overviewMetaSecondaryLabel.text = card.metaSecondary
        overviewMetaSecondaryLabel.isVisible = card.metaSecondary.isNotBlank()
        renderMetricChips(card)
        overviewSummaryArea.text = card.summary.ifBlank {
            localizationService.text("요약 정보가 없습니다.", "No summary available.")
        }
        overviewSummaryArea.caretPosition = 0
        currentOverviewUrl = card.siteUrl
        overviewSiteButton.isVisible = !currentOverviewUrl.isNullOrBlank()
        emptyStatePanel.isVisible = false
        contentPanel.isVisible = true
    }

    private fun hideOverview() {
        overviewTitleLabel.text = ""
        overviewMetaPrimaryLabel.text = ""
        overviewMetaSecondaryLabel.text = ""
        overviewMetricWrap.removeAll()
        overviewMetaSecondaryLabel.isVisible = false
        currentOverviewUrl = null
        overviewSiteButton.isVisible = false
        emptyStatePanel.removeAll()
        emptyStatePanel.add(
            createEmptyState(
                localizationService.text("관심종목에서 종목을 선택하면 개요를 표시합니다.", "Select a ticker from the watchlist to view overview.")
            ),
            BorderLayout.CENTER
        )
        emptyStatePanel.isVisible = true
        contentPanel.isVisible = false
        revalidate()
        repaint()
    }

    private fun showLoadingState() {
        overviewTitleLabel.text = ""
        overviewMetaPrimaryLabel.text = ""
        overviewMetaSecondaryLabel.text = ""
        overviewMetricWrap.removeAll()
        currentOverviewUrl = null
        overviewSiteButton.isVisible = false
        emptyStatePanel.removeAll()
        emptyStatePanel.add(
            createEmptyState(localizationService.text("개요를 불러오는 중...", "Loading overview...")),
            BorderLayout.CENTER
        )
        emptyStatePanel.isVisible = true
        contentPanel.isVisible = false
        revalidate()
        repaint()
    }

    private fun renderMetricChips(card: TickerOverviewCard) {
        overviewMetricWrap.removeAll()
        val chips = (card.primaryMetrics.split(" · ") + card.secondaryMetrics.split(" · "))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(8)

        chips.forEach { metric ->
            overviewMetricWrap.add(
                JLabel(metric).apply {
                    foreground = JBColor.foreground()
                    background = JBColor(Color(248, 250, 252), Color(50, 54, 60))
                    isOpaque = true
                    border = JBUI.Borders.compound(
                        JBUI.Borders.customLine(JBColor(Color(220, 226, 232), Color(72, 78, 86)), 1),
                        JBUI.Borders.empty(4, 8)
                    )
                }
            )
        }
        overviewMetricWrap.revalidate()
        overviewMetricWrap.repaint()
    }

    private fun buildHeaderPanel(): JComponent {
        return JPanel(BorderLayout(0, 10)).apply {
            isOpaque = false
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(titleLabel, BorderLayout.NORTH)
                    add(statusLabel, BorderLayout.SOUTH)
                },
                BorderLayout.NORTH
            )
            add(
                JPanel(BorderLayout(0, 10)).apply {
                    border = JBUI.Borders.compound(
                        JBUI.Borders.customLine(JBColor.border(), 1),
                        JBUI.Borders.empty(12)
                    )
                    add(
                        JPanel(BorderLayout(0, 8)).apply {
                            isOpaque = false
                            add(
                                JPanel().apply {
                                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                    isOpaque = false
                                    add(overviewTitleLabel)
                                    add(Box.createVerticalStrut(4))
                                    add(overviewMetaPrimaryLabel)
                                    add(overviewMetaSecondaryLabel)
                                },
                                BorderLayout.NORTH
                            )
                            add(overviewMetricWrap, BorderLayout.CENTER)
                            add(
                                JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                                    isOpaque = false
                                    add(overviewSiteButton)
                                },
                                BorderLayout.EAST
                            )
                        },
                        BorderLayout.NORTH
                    )
                    add(
                        JPanel(VerticalLayout(4)).apply {
                            isOpaque = false
                            add(summaryTitleLabel)
                            add(
                                JBScrollPane(overviewSummaryArea).apply {
                                    border = BorderFactory.createLineBorder(JBColor.border())
                                    horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                                    preferredSize = Dimension(0, 96)
                                    minimumSize = Dimension(0, 96)
                                }
                            )
                        },
                        BorderLayout.CENTER
                    )
                },
                BorderLayout.CENTER
            )
        }
    }

    private fun createEmptyState(message: String): JComponent {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(24)
            )
            add(
                JLabel(message, JLabel.CENTER).apply {
                    foreground = JBColor.GRAY
                    horizontalAlignment = JLabel.CENTER
                    alignmentX = Component.CENTER_ALIGNMENT
                },
                BorderLayout.CENTER
            )
        }
    }
}
