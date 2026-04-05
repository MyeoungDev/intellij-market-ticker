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
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
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
    private val overviewMetricWrap = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
    private val overviewSummaryArea = JTextArea()
    private val overviewSiteButton = JButton(localizationService.text("공식 사이트", "Official Site"))
    private var currentOverviewUrl: String? = null

    init {
        border = JBUI.Borders.empty(8)
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size2D + 1f)
        statusLabel.foreground = JBColor.GRAY
        overviewTitleLabel.font = overviewTitleLabel.font.deriveFont(overviewTitleLabel.font.size2D + 1f)
        overviewMetaPrimaryLabel.foreground = JBColor.GRAY
        overviewMetaSecondaryLabel.foreground = JBColor.GRAY
        overviewMetricWrap.isOpaque = false
        overviewSummaryArea.isEditable = false
        overviewSummaryArea.isOpaque = false
        overviewSummaryArea.lineWrap = true
        overviewSummaryArea.wrapStyleWord = true
        overviewSummaryArea.rows = 5
        overviewSummaryArea.border = JBUI.Borders.empty()
        overviewSummaryArea.foreground = JBColor.foreground()
        overviewSummaryArea.font = overviewSummaryArea.font.deriveFont(12f)
        overviewSiteButton.isVisible = false
        overviewSiteButton.addActionListener { currentOverviewUrl?.let(BrowserUtil::browse) }

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
                add(
                    JPanel(BorderLayout(0, 8)).apply {
                        border = JBUI.Borders.compound(
                            JBUI.Borders.customLine(JBColor.border(), 1),
                            JBUI.Borders.empty(8)
                        )
                        add(
                            JPanel().apply {
                                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                                isOpaque = false
                                add(overviewTitleLabel)
                                add(overviewMetaPrimaryLabel)
                                add(overviewMetaSecondaryLabel)
                                add(overviewMetricWrap)
                            },
                            BorderLayout.NORTH
                        )
                        add(overviewSummaryArea, BorderLayout.CENTER)
                        add(
                            JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                                isOpaque = false
                                add(overviewSiteButton)
                            },
                            BorderLayout.SOUTH
                        )
                    },
                    BorderLayout.CENTER
                )
            },
            BorderLayout.CENTER
        )
        hideOverview()
    }

    fun showTicker(ticker: Ticker) {
        requestJob?.cancel()
        titleLabel.text = localizationService.text("${ticker.name} 개요", "${ticker.name} Overview")
        statusLabel.text = localizationService.text("개요를 불러오는 중...", "Loading overview...")
        hideOverview()

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
        statusLabel.text = if (viewData.overviewCard == null) {
            localizationService.text("표시할 개요가 없습니다.", "No overview available.")
        } else {
            localizationService.text("핵심 개요를 표시합니다.", "Showing the key overview.")
        }
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
    }

    private fun hideOverview() {
        overviewTitleLabel.text = ""
        overviewMetaPrimaryLabel.text = ""
        overviewMetaSecondaryLabel.text = ""
        overviewMetricWrap.removeAll()
        overviewSummaryArea.text = localizationService.text(
            "뉴스와 분리된 종목 개요 전용 패널입니다. 관심종목을 선택하면 네이버 기반 종목 요약과 핵심 지표를 보여줍니다.",
            "This is a dedicated stock overview panel separate from news. Select a watchlist ticker to see the Naver-backed summary and key metrics."
        )
        overviewMetaSecondaryLabel.isVisible = false
        currentOverviewUrl = null
        overviewSiteButton.isVisible = false
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
}
