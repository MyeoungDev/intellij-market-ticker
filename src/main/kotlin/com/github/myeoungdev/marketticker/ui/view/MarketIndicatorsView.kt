package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MoneyDisplayFormatter
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
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

/**
 * 환율과 주요 시장 지표를 섹션별 카드 그리드로 보여주는 탭입니다.
 */
class MarketIndicatorsView : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val moneyDisplayFormatter = MoneyDisplayFormatter()
    private var latestIndicators: List<MarketIndicator> = emptyList()

    private val titleLabel = JLabel(localizationService.text("환율 및 주요 지표", "FX & Major Indicators"))
    private val subtitleLabel = JLabel(
        localizationService.text(
            "환율, 지수, 원자재를 한 화면에서 확인합니다.",
            "View exchange rates, indices, and commodities in one place."
        )
    )

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    private val emptyLabel = JLabel(
        localizationService.text("표시할 지표가 없습니다.", "No indicators available."),
        SwingConstants.CENTER
    )

    init {
        border = JBUI.Borders.empty(10)
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, titleLabel.font.size2D + 3f)
        subtitleLabel.foreground = Color(140, 140, 140)

        val header = JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.NORTH)
            add(subtitleLabel, BorderLayout.SOUTH)
        }

        val scrollPane = com.intellij.ui.components.JBScrollPane(contentPanel).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(header, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                refreshLayout()
            }
        })

        renderIndicators(emptyList())
    }

    fun renderIndicators(indicators: List<MarketIndicator>) {
        latestIndicators = indicators
        refreshLayout()
    }

    override fun dispose() {
        // No-op. The view only renders externally supplied data.
    }

    private fun refreshLayout() {
        contentPanel.removeAll()

        val sections = groupMarketIndicators(latestIndicators)
        if (sections.isEmpty()) {
            contentPanel.add(emptyStatePanel())
        } else {
            sections.forEachIndexed { index, section ->
                contentPanel.add(createSectionPanel(section))
                if (index < sections.lastIndex) {
                    contentPanel.add(Box.createVerticalStrut(10))
                }
            }
        }

        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun emptyStatePanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(24)
            emptyLabel.foreground = Color(150, 150, 150)
            add(emptyLabel, BorderLayout.CENTER)
        }
    }

    private fun createSectionPanel(section: MarketIndicatorSection): JPanel {
        return JPanel(BorderLayout(0, 8)).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(58, 58, 58)),
                JBUI.Borders.empty(10, 12)
            )
            add(createSectionHeader(section), BorderLayout.NORTH)
            add(createCardGridPanel(section.indicators), BorderLayout.CENTER)
        }
    }

    private fun createSectionHeader(section: MarketIndicatorSection): JPanel {
        val title = JLabel(sectionTitle(section.category)).apply {
            font = font.deriveFont(Font.BOLD, font.size2D + 1f)
        }
        val count = JLabel("${section.indicators.size}").apply {
            foreground = Color(150, 150, 150)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(title, BorderLayout.WEST)
            add(count, BorderLayout.EAST)
        }
    }

    private fun createCardGridPanel(indicators: List<MarketIndicator>): JPanel {
        val availableWidth = (contentPanel.width.takeIf { it > 0 } ?: width).coerceAtLeast(0)
        val columns = calculateIndicatorCardColumns(availableWidth, indicators.size)
        val rows = ((indicators.size + columns - 1) / columns).coerceAtLeast(1)

        return JPanel(GridLayout(rows, columns, 8, 8)).apply {
            isOpaque = false
            indicators.forEach { add(createIndicatorCard(it)) }
            repeat(rows * columns - indicators.size) {
                add(JPanel().apply { isOpaque = false })
            }
        }
    }

    private fun createIndicatorCard(indicator: MarketIndicator): JPanel {
        val card = JPanel(BorderLayout(0, 8)).apply {
            isOpaque = true
            background = rowBackground(indicator.changeRate)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor(indicator.changeRate)),
                JBUI.Borders.empty(10, 10)
            )
            preferredSize = Dimension(166, 76)
        }

        val accent = JPanel().apply {
            preferredSize = Dimension(3, 0)
            minimumSize = Dimension(3, 0)
            maximumSize = Dimension(3, Int.MAX_VALUE)
            background = accentColor(indicator.changeRate)
            isOpaque = true
        }

        val titleLabel = JLabel(displayIndicatorName(indicator)).apply {
            font = font.deriveFont(Font.BOLD, font.size2D + 0.5f)
        }
        val priceLabel = JLabel(formatPrice(indicator)).apply {
            font = font.deriveFont(Font.BOLD, font.size2D + 1.5f)
            foreground = priceColor(indicator.changeRate)
        }
        val changeLabel = JLabel(formatChange(indicator.changeRate)).apply {
            foreground = priceColor(indicator.changeRate)
        }

        val textPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(titleLabel)
            add(priceLabel)
            add(changeLabel)
        }

        accent.border = JBUI.Borders.emptyRight(8)
        card.add(accent, BorderLayout.WEST)
        card.add(textPanel, BorderLayout.CENTER)
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
        return moneyDisplayFormatter.formatAmount(indicator.currentPrice, CurrencyType.of(indicator.unit.orEmpty()), 2)
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
            changeRate > 0 -> Color(38, 48, 40)
            changeRate < 0 -> Color(48, 38, 38)
            else -> Color(40, 40, 40)
        }
    }

    private fun borderColor(changeRate: Double): Color {
        return when {
            changeRate > 0 -> Color(69, 112, 79)
            changeRate < 0 -> Color(112, 69, 69)
            else -> Color(62, 62, 62)
        }
    }

    private fun accentColor(changeRate: Double): Color {
        return when {
            changeRate > 0 -> Color(96, 154, 110)
            changeRate < 0 -> Color(154, 96, 96)
            else -> Color(96, 96, 96)
        }
    }

    private fun priceColor(changeRate: Double): Color {
        return when {
            changeRate > 0 -> Color(155, 228, 165)
            changeRate < 0 -> Color(240, 166, 166)
            else -> Color(220, 220, 220)
        }
    }
}
