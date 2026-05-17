package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MoneyDisplayFormatter
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.Timer

/**
 * 환율과 주요 시장 지표를 섹션별 카드 그리드로 보여주는 탭입니다.
 */
class MarketIndicatorsView : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val moneyDisplayFormatter = MoneyDisplayFormatter()
    private var latestIndicators: List<MarketIndicator> = emptyList()
    private var lastLayoutSignature: LayoutSignature? = null
    private var layoutRefreshInProgress = false

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    private val emptyLabel = JLabel(
        localizationService.text("표시할 지표가 없습니다.", "No indicators available."),
        SwingConstants.CENTER
    )

    private val layoutRefreshTimer = Timer(120) {
        refreshLayout(force = false)
    }.apply {
        isRepeats = false
    }

    init {
        border = JBUI.Borders.empty(8)

        val scrollPane = com.intellij.ui.components.JBScrollPane(contentPanel).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(scrollPane, BorderLayout.CENTER)

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                scheduleLayoutRefresh()
            }
        })

        renderIndicators(emptyList())
    }

    fun renderIndicators(indicators: List<MarketIndicator>) {
        latestIndicators = indicators
        refreshLayout(force = true)
    }

    override fun dispose() {
        layoutRefreshTimer.stop()
        // No-op. The view only renders externally supplied data.
    }

    private fun scheduleLayoutRefresh() {
        if (!isShowing) {
            refreshLayout(force = true)
            return
        }

        layoutRefreshTimer.restart()
    }

    private fun refreshLayout(force: Boolean) {
        if (layoutRefreshInProgress) return

        val sections = groupMarketIndicators(latestIndicators)
        val signature = LayoutSignature.from(sections, contentPanel.width.takeIf { it > 0 } ?: width)
        if (!force && signature == lastLayoutSignature) {
            return
        }

        layoutRefreshInProgress = true
        contentPanel.removeAll()
        try {
            if (sections.isEmpty()) {
                contentPanel.add(emptyStatePanel())
            } else {
                sections.forEachIndexed { index, section ->
                    contentPanel.add(createSectionPanel(section))
                    if (index < sections.lastIndex) {
                        contentPanel.add(Box.createVerticalStrut(8))
                    }
                }
            }

            lastLayoutSignature = signature
            contentPanel.revalidate()
            contentPanel.repaint()
        } finally {
            layoutRefreshInProgress = false
        }
    }

    private fun emptyStatePanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(18)
            emptyLabel.foreground = Color(150, 150, 150)
            add(emptyLabel, BorderLayout.CENTER)
        }
    }

    private fun createSectionPanel(section: MarketIndicatorSection): JPanel {
        return JPanel(BorderLayout(0, 8)).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(8, 10)
            )
            add(createSectionHeader(section), BorderLayout.NORTH)
            add(createCardGridPanel(section.indicators), BorderLayout.CENTER)
        }
    }

    private fun createSectionHeader(section: MarketIndicatorSection): JPanel {
        val title = JLabel(sectionTitle(section.category)).apply {
            font = font.deriveFont(Font.BOLD, font.size2D)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(title, BorderLayout.WEST)
        }
    }

    private fun createCardGridPanel(indicators: List<MarketIndicator>): JPanel {
        val availableWidth = (contentPanel.width.takeIf { it > 0 } ?: width).coerceAtLeast(0)
        val columns = calculateIndicatorCardColumns(availableWidth, indicators.size)
        val rows = ((indicators.size + columns - 1) / columns).coerceAtLeast(1)

        return JPanel(GridLayout(rows, columns, 6, 6)).apply {
            isOpaque = false
            indicators.forEach { add(createIndicatorCard(it)) }
            repeat(rows * columns - indicators.size) {
                add(JPanel().apply { isOpaque = false })
            }
        }
    }

    private data class LayoutSignature(
        val sectionSizes: List<Int>,
        val columnsBySection: List<Int>,
    ) {
        companion object {
            fun from(sections: List<MarketIndicatorSection>, availableWidth: Int): LayoutSignature {
                return LayoutSignature(
                    sectionSizes = sections.map { it.indicators.size },
                    columnsBySection = sections.map { section ->
                        calculateIndicatorCardColumns(availableWidth, section.indicators.size)
                    }
                )
            }
        }
    }

    private fun createIndicatorCard(indicator: MarketIndicator): JPanel {
        val card = JPanel(BorderLayout(0, 8)).apply {
            isOpaque = true
            background = rowBackground(indicator.changeRate)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor(indicator.changeRate)),
                JBUI.Borders.empty(7, 10)
            )
            preferredSize = Dimension(144, 58)
        }

        val titleLabel = JLabel(displayIndicatorName(indicator)).apply {
            font = font.deriveFont(Font.BOLD, font.size2D)
        }
        val priceLabel = JLabel(formatPrice(indicator)).apply {
            font = font.deriveFont(Font.BOLD, font.size2D + 0.5f)
            foreground = priceColor(indicator.changeRate)
        }
        val changeLabel = JLabel(formatChange(indicator.changeRate)).apply {
            foreground = priceColor(indicator.changeRate)
            font = font.deriveFont(font.size2D - 1f)
        }

        val contentPanel = JPanel(BorderLayout(0, 2)).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.NORTH)
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(priceLabel, BorderLayout.WEST)
                    add(changeLabel, BorderLayout.EAST)
                },
                BorderLayout.CENTER
            )
        }

        card.add(contentPanel, BorderLayout.CENTER)
        return card
    }

    private fun sectionTitle(category: IndicatorCategory): String {
        return when (category) {
            IndicatorCategory.EXCHANGE_RATE -> localizationService.text("환율", "FX")
            IndicatorCategory.DOMESTIC_INDEX -> localizationService.text("국내 지수", "Domestic")
            IndicatorCategory.WORLD_INDEX -> localizationService.text("해외 지수", "Global")
            IndicatorCategory.METAL -> localizationService.text("금속", "Metals")
            IndicatorCategory.ENERGY -> localizationService.text("에너지", "Energy")
        }
    }

    private fun displayIndicatorName(indicator: MarketIndicator): String {
        return when (indicator.code.uppercase()) {
            ".INX", "SPX", "S&P500", "S&P 500" -> "S&P500"
            ".IXIC", "IXIC", "NASDAQ", "NASDAQ COMPOSITE" -> "NASDAQ"
            ".DJI", "DJI", "DJIA", "DOW JONES" -> "DOW"
            else -> indicator.name
        }
    }

    private fun formatPrice(indicator: MarketIndicator): String {
        return formatIndicatorPrice(
            value = indicator.currentPrice,
            unit = indicator.unit,
            formatDecimal = localizationService::formatDecimal,
            formatAmount = moneyDisplayFormatter::formatAmount
        )
    }

    private fun formatChange(changeRate: Double): String {
        val prefix = when {
            changeRate > 0 -> "+"
            changeRate < 0 -> "-"
            else -> ""
        }
        return "$prefix${localizationService.formatPercentFixed(kotlin.math.abs(changeRate), 2)}"
    }

    private fun rowBackground(changeRate: Double): Color {
        return when {
            changeRate != 0.0 -> Color(40, 40, 40)
            else -> Color(39, 39, 39)
        }
    }

    private fun borderColor(changeRate: Double): Color {
        return when {
            changeRate > 0 -> Color(78, 102, 82)
            changeRate < 0 -> Color(102, 78, 78)
            else -> Color(72, 72, 72)
        }
    }

    private fun priceColor(changeRate: Double): Color {
        return when {
            changeRate > 0 -> Color(167, 233, 176)
            changeRate < 0 -> Color(242, 173, 173)
            else -> Color(224, 224, 224)
        }
    }
}
