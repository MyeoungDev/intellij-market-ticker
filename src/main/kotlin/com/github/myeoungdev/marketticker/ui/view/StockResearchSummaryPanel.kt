package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.ResearchFacadeService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.research.ResearchArticle
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

class StockResearchSummaryPanel : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val researchFacadeService = service<ResearchFacadeService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var requestJob: Job? = null

    private val titleLabel = JLabel(localizationService.text("선택 종목 리서치", "Selected Stock Research"))
    private val statusLabel = JLabel(localizationService.text("관심종목을 선택하면 리서치를 표시합니다.", "Select a watchlist ticker to see research."))
    private val model = CollectionListModel<ResearchArticle>()
    private val list = JBList(model)

    init {
        border = JBUI.Borders.empty(8)

        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size2D + 1f)
        statusLabel.foreground = JBColor.GRAY

        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.visibleRowCount = 3
        list.cellRenderer = SimpleListCellRenderer.create { label, value, _ ->
            val article = value ?: return@create
            val meta = listOfNotNull(
                article.brokerName.takeIf { it.isNotBlank() },
                article.writeDate.takeIf { it.isNotBlank() }
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
                    list.selectedValue?.endUrl?.takeIf { it.isNotBlank() }?.let(BrowserUtil::browse)
                }
            }
        })

        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(titleLabel, BorderLayout.NORTH)
                add(statusLabel, BorderLayout.SOUTH)
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
        titleLabel.text = localizationService.text(
            "${ticker.name} 리서치",
            "${ticker.name} Research"
        )
        statusLabel.text = localizationService.text("리서치를 불러오는 중...", "Loading research...")
        model.replaceAll(
            listOf(
                ResearchArticle(title = localizationService.text("리서치를 불러오는 중...", "Loading research..."))
            )
        )

        requestJob = scope.launch {
            val viewData = researchFacadeService.loadTickerResearchSummary(ticker)
            withContext(Dispatchers.Main) {
                if (!isActive) return@withContext
                if (viewData.articles.isEmpty()) {
                    model.replaceAll(
                        listOf(
                            ResearchArticle(
                                title = localizationService.text("표시할 리서치가 없습니다.", "No research available.")
                            )
                        )
                    )
                } else {
                    model.replaceAll(viewData.articles)
                    if (list.selectedIndex == -1) {
                        list.selectedIndex = 0
                    }
                }
                statusLabel.text = localizationService.text(viewData.statusMessage, viewData.statusMessage)
            }
        }
    }

    override fun dispose() {
        requestJob?.cancel()
        scope.cancel()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
