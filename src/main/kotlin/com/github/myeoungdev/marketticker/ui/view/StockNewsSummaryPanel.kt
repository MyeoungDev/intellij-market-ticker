package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverClient
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockBasic
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockOverview
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverNewsArticle
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchArticle
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 *
 * 선택된 종목 기준 최근 뉴스 헤드라인만 가볍게 보여줍니다.
 */
class StockNewsSummaryPanel : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val naverClient = NaverClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val titleLabel = JLabel(localizationService.text("선택 종목 뉴스", "Selected Stock News"))
    private val statusLabel = JLabel(localizationService.text("관심종목을 선택하면 뉴스를 표시합니다.", "Select a watchlist ticker to see news."))
    private val overviewTitleLabel = JLabel()
    private val overviewMetaLabel = JLabel()
    private val overviewMetricsLabel = JLabel()
    private val overviewSummaryArea = JTextArea()
    private val overviewToggleButton = JButton()
    private val overviewSiteButton = JButton(localizationService.text("공식 사이트", "Official Site"))
    private val overviewPanel = JPanel(BorderLayout(0, 6))
    private val model = CollectionListModel<NaverNewsArticle>()
    private val list = JBList(model)
    private var currentOverviewUrl: String? = null
    private var isOverviewExpanded: Boolean = false

    init {
        border = JBUI.Borders.empty(8)

        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size2D + 1f)
        statusLabel.foreground = JBColor.GRAY
        overviewTitleLabel.font = overviewTitleLabel.font.deriveFont(overviewTitleLabel.font.size2D + 1f)
        overviewMetaLabel.foreground = JBColor.GRAY
        overviewMetricsLabel.foreground = JBColor.foreground()
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
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(
                        JPanel().apply {
                            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                            isOpaque = false
                            add(overviewTitleLabel)
                            add(overviewMetaLabel)
                            add(overviewMetricsLabel)
                        },
                        BorderLayout.CENTER
                    )
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                            isOpaque = false
                            add(overviewToggleButton)
                            add(overviewSiteButton)
                        },
                        BorderLayout.EAST
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
                article.badgeLabel.takeIf { !it.isNullOrBlank() },
                article.officeHname.takeIf { !it.isNullOrBlank() },
                article.datetime?.takeIf { it.isNotBlank() }?.let(::formatDate)
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
                    list.selectedValue?.articleUrl()?.let(BrowserUtil::browse)
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
        titleLabel.text = localizationService.text("${ticker.name} 뉴스", "${ticker.name} News")
        statusLabel.text = localizationService.text("뉴스를 불러오는 중...", "Loading news...")
        hideOverview()
        model.replaceAll(
            listOf(
                NaverNewsArticle(title = localizationService.text("뉴스를 불러오는 중...", "Loading news..."))
            )
        )

        scope.launch {
            val overview = if (!ticker.marketType.isKoreanMarket() && !ticker.marketType.isCryptoMarket()) {
                naverClient.fetchForeignStockOverview(ticker.tradingSymbol)
            } else {
                null
            }
            val basic = if (!ticker.marketType.isKoreanMarket() && !ticker.marketType.isCryptoMarket()) {
                naverClient.fetchForeignStockBasic(ticker.tradingSymbol)
            } else {
                null
            }
            val research = if (ticker.marketType.isKoreanMarket()) {
                naverClient.fetchStockResearch(ticker.symbol, size = 1).firstOrNull()
            } else {
                null
            }
            val articles = loadArticles(ticker)
            withContext(Dispatchers.Main) {
                renderOverview(ticker, overview, basic, research)
                if (articles.isEmpty()) {
                    model.replaceAll(
                        listOf(
                            NaverNewsArticle(title = localizationService.text("표시할 뉴스가 없습니다.", "No news available."))
                        )
                    )
                    statusLabel.text = localizationService.text("최근 뉴스 없음", "No recent news")
                } else {
                    model.replaceAll(articles)
                    if (list.selectedIndex == -1) {
                        list.selectedIndex = 0
                    }
                    statusLabel.text = localizationService.text(
                        "최근 뉴스 ${articles.size}건",
                        "${articles.size} recent items"
                    )
                }
            }
        }
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun loadArticles(ticker: Ticker): List<NaverNewsArticle> {
        if (ticker.marketType.isCryptoMarket()) return emptyList()

        val localItemCode = if (ticker.marketType.isKoreanMarket()) ticker.symbol else ticker.tradingSymbol
        val domesticArticles = naverClient.fetchDomesticDetailNews(itemCode = localItemCode, page = 1, pageSize = 15)
        val foreignArticles = if (ticker.marketType.isKoreanMarket()) {
            emptyList()
        } else {
            naverClient.fetchForeignStockNews(reutersCode = ticker.tradingSymbol, page = 1, pageSize = 15)
        }

        return StockNewsSummaryFormatter.mergeArticles(domesticArticles, foreignArticles)
    }

    private fun renderOverview(
        ticker: Ticker,
        overview: NaverForeignStockOverview?,
        basic: NaverForeignStockBasic?,
        research: NaverResearchArticle?
    ) {
        val card = StockNewsSummaryFormatter.buildOverviewCard(ticker, overview, basic, research)
        if (card == null) {
            hideOverview()
            return
        }

        overviewTitleLabel.text = card.title
        overviewMetaLabel.text = card.meta
        overviewMetricsLabel.text = card.metrics
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
        overviewMetaLabel.text = ""
        overviewMetricsLabel.text = ""
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
        overviewToggleButton.text = localizationService.text(
            if (isOverviewExpanded) "접기" else "더보기",
            if (isOverviewExpanded) "Less" else "More"
        )
        overviewPanel.revalidate()
        overviewPanel.repaint()
    }
}
