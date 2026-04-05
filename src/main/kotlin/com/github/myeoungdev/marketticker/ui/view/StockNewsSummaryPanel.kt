package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.github.myeoungdev.marketticker.application.model.news.TickerNewsSummaryViewData
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
import javax.swing.JLabel
import javax.swing.JPanel
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
    private val model = CollectionListModel<NewsArticle>()
    private val list = JBList(model)

    init {
        border = JBUI.Borders.empty(8)

        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size2D + 1f)
        statusLabel.foreground = JBColor.GRAY

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
}
