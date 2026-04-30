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

    private val overviewTitleLabel = JLabel()
    private val statusLabel = JLabel(localizationService.text("관심종목을 선택하면 개요를 표시합니다.", "Select a watchlist ticker to see overview."))
    private val overviewMetaPrimaryLabel = JLabel()
    private val overviewMetaSecondaryLabel = JLabel()
    private val overviewMetricWrap = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
    private val overviewSummaryArea = JTextArea()
    private val overviewSiteButton = JButton(localizationService.text("공식 사이트", "Official Site"))
    private val contentPanel = JPanel(BorderLayout(0, 10))
    private val emptyStatePanel = JPanel(BorderLayout())
    private val bodyPanel = JPanel()
    private var currentOverviewUrl: String? = null

    init {
        border = JBUI.Borders.empty(8)
        overviewTitleLabel.font = overviewTitleLabel.font.deriveFont(Font.BOLD, overviewTitleLabel.font.size2D + 2f)
        statusLabel.foreground = JBColor.GRAY
        overviewMetaPrimaryLabel.foreground = JBColor.GRAY
        overviewMetaSecondaryLabel.foreground = JBColor.GRAY
        overviewMetricWrap.isOpaque = false
        overviewSummaryArea.isEditable = false
        overviewSummaryArea.isOpaque = false
        overviewSummaryArea.lineWrap = true
        overviewSummaryArea.wrapStyleWord = true
        overviewSummaryArea.rows = 2
        overviewSummaryArea.foreground = JBColor.GRAY
        overviewSummaryArea.font = overviewSummaryArea.font.deriveFont(11.5f)
        overviewSiteButton.isVisible = false
        overviewSiteButton.addActionListener { currentOverviewUrl?.let(BrowserUtil::browse) }
        preferredSize = null
        minimumSize = Dimension(0, 100)

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
        overviewTitleLabel.text = ticker.name
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

        overviewTitleLabel.text = card.title.ifBlank { overviewTitleLabel.text }
        overviewMetaPrimaryLabel.text = card.metaPrimary
        overviewMetaSecondaryLabel.text = card.metaSecondary
        overviewMetaSecondaryLabel.isVisible = card.metaSecondary.isNotBlank()
        renderMetricChips(card)
        overviewSummaryArea.text = card.summary.takeIf { it.isNotBlank() } ?: ""
        overviewSummaryArea.isVisible = overviewSummaryArea.text.isNotBlank()
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
        overviewSummaryArea.text = ""
        overviewSummaryArea.isVisible = false
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
        overviewMetaPrimaryLabel.text = ""
        overviewMetaSecondaryLabel.text = ""
        overviewMetricWrap.removeAll()
        overviewSummaryArea.text = ""
        overviewSummaryArea.isVisible = false
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
        return JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(statusLabel, BorderLayout.SOUTH)
                },
                BorderLayout.NORTH
            )
            add(
                JPanel(BorderLayout(0, 4)).apply {
                    border = JBUI.Borders.compound(
                        JBUI.Borders.customLine(JBColor.border(), 1),
                        JBUI.Borders.empty(6)
                    )
                    add(
                        JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.Y_AXIS)
                            isOpaque = false
                            add(overviewTitleLabel)
                            add(Box.createVerticalStrut(4))
                            add(overviewMetaPrimaryLabel)
                            add(Box.createVerticalStrut(4))
                            add(overviewMetaSecondaryLabel)
                        },
                        BorderLayout.NORTH
                    )
                    add(
                        JPanel(BorderLayout(0, 2)).apply {
                            isOpaque = false
                            add(overviewMetricWrap, BorderLayout.CENTER)
                            add(
                                JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                                    isOpaque = false
                                    add(overviewSiteButton)
                                },
                                BorderLayout.EAST
                            )
                        },
                        BorderLayout.CENTER
                    )
                    add(
                        overviewSummaryArea.apply {
                            border = JBUI.Borders.emptyTop(6)
                        },
                        BorderLayout.SOUTH
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
