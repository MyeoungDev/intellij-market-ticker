package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.*
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

class HeatmapView : JPanel(BorderLayout()) {

    private val gridPanel = JPanel()
    private val emptyLabel = JBLabel("관심 종목이 없습니다.", SwingConstants.CENTER)

    init {
        background = Color(30, 30, 30)
        add(gridPanel, BorderLayout.CENTER)
    }

    fun updateHeatmap(prices: List<TickerPrice>) {
        // EDT에서 실행 보장
        if (ApplicationManager.getApplication().isDispatchThread) {
            updateHeatmapInternal(prices)
        } else {
            ApplicationManager.getApplication().invokeLater {
                updateHeatmapInternal(prices)
            }
        }
    }

    private fun updateHeatmapInternal(prices: List<TickerPrice>) {
        logger.debug { "Updating grid heatmap with ${prices.size} prices" }

        removeAll()

        if (prices.isEmpty()) {
            logger.warn { "No prices to display in heatmap" }
            add(emptyLabel, BorderLayout.CENTER)
            revalidate()
            repaint()
            return
        }

        // 유효한 데이터만 필터링
        val validPrices = prices.filter { price ->
            price.name.isNotBlank()
        }

        if (validPrices.isEmpty()) {
            logger.warn { "All prices filtered out as invalid" }
            add(JBLabel("유효한 데이터가 없습니다.", SwingConstants.CENTER), BorderLayout.CENTER)
            revalidate()
            repaint()
            return
        }

        // 그리드 행/열 계산 (정사각형에 가깝게)
        val itemCount = validPrices.size
        val columns = calculateOptimalColumns(itemCount)
        val rows = ceil(itemCount.toDouble() / columns).toInt()

        logger.debug { "Grid layout: ${rows}x${columns} for $itemCount items" }

        // GridLayout 설정 (간격 2px)
        gridPanel.layout = GridLayout(rows, columns, 2, 2)
        gridPanel.background = Color(30, 30, 30)
        gridPanel.removeAll()

        // 각 종목에 대한 셀 생성
        validPrices.forEach { price ->
            val cell = createPriceCell(price)
            gridPanel.add(cell)
        }

        add(gridPanel, BorderLayout.CENTER)
        revalidate()
        repaint()

        logger.info { "Grid heatmap updated successfully with ${validPrices.size} items" }
    }

    /**
     * 최적의 열 개수 계산 (정사각형 그리드에 가깝게)
     */
    private fun calculateOptimalColumns(itemCount: Int): Int {
        return when {
            itemCount <= 1 -> 1
            itemCount <= 4 -> 2
            itemCount <= 9 -> 3
            itemCount <= 16 -> 4
            itemCount <= 25 -> 5
            else -> ceil(sqrt(itemCount.toDouble())).toInt()
        }
    }

    /**
     * 개별 가격 셀 생성 (블룸버그 스타일)
     */
    private fun createPriceCell(price: TickerPrice): JPanel {
        val cell = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        // 배경색 설정 (등락률 기반)
        val bgColor = getBloombergColor(price.changeRate)
        cell.background = bgColor
        cell.border = BorderFactory.createLineBorder(Color.BLACK, 2)

        // 종목명 라벨
        val nameLabel = JBLabel(price.name, SwingConstants.CENTER).apply {
            foreground = Color.WHITE
            font = Font("SansSerif", Font.BOLD, 14)
        }

        // 현재가 라벨
        val priceLabel = JBLabel(formatPrice(price), SwingConstants.CENTER).apply {
            foreground = Color.WHITE
            font = Font("SansSerif", Font.BOLD, 16)
        }

        // 등락률 라벨
        val changeRateLabel = JBLabel(formatChangeRate(price.changeRate), SwingConstants.CENTER).apply {
            foreground = Color.WHITE
            font = Font("SansSerif", Font.BOLD, 14)
        }

        // 고가/저가 라벨 (작은 글씨)
        val rangeLabel = JBLabel(formatRange(price), SwingConstants.CENTER).apply {
            foreground = Color(220, 220, 220)
            font = Font("SansSerif", Font.PLAIN, 10)
        }

        // 레이아웃 설정 (세로로 쌓기)
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.insets = Insets(5, 5, 2, 5)
        gbc.anchor = GridBagConstraints.CENTER
        cell.add(nameLabel, gbc)

        gbc.gridy = 1
        gbc.insets = Insets(2, 5, 2, 5)
        cell.add(priceLabel, gbc)

        gbc.gridy = 2
        gbc.insets = Insets(2, 5, 2, 5)
        cell.add(changeRateLabel, gbc)

        gbc.gridy = 3
        gbc.insets = Insets(2, 5, 5, 5)
        cell.add(rangeLabel, gbc)

        return cell
    }

    /**
     * 현재가 포맷팅
     */
    private fun formatPrice(price: TickerPrice): String {
        return if (price.currentPrice > 0.0) {
            when {
                price.currentPrice >= 1000 -> String.format("%,.0f", price.currentPrice)
                price.currentPrice >= 10 -> String.format("%.2f", price.currentPrice)
                else -> String.format("%.4f", price.currentPrice)
            }
        } else {
            "-"
        }
    }

    /**
     * 등락률 포맷팅
     */
    private fun formatChangeRate(changeRate: Double): String {
        val sign = when {
            changeRate > 0 -> "+"
            else -> ""
        }
        return "${sign}${String.format("%.2f", changeRate)}%"
    }

    /**
     * 고가/저가 범위 포맷팅
     */
    private fun formatRange(price: TickerPrice): String {
        if (price.highPrice <= 0.0 || price.lowPrice <= 0.0) {
            return ""
        }
        return "H ${formatSimplePrice(price.highPrice)} / L ${formatSimplePrice(price.lowPrice)}"
    }

    private fun formatSimplePrice(price: Double): String {
        return when {
            price >= 1000 -> String.format("%,.0f", price)
            price >= 10 -> String.format("%.1f", price)
            else -> String.format("%.2f", price)
        }
    }

    /**
     * 블룸버그 스타일 색상 (초록=상승, 빨강=하락, 회색=보합)
     */
    private fun getBloombergColor(changeRate: Double): Color {
        val absChange = abs(changeRate)

        // 보합 기준 (0.01% 이하)
        if (absChange < 0.01) {
            return Color(102, 102, 102) // #666666
        }

        // 강도 계산 (5% 기준)
        val maxChange = 5.0
        val intensity = min(1.0, absChange / maxChange).coerceAtLeast(0.3)

        return when {
            changeRate > 0 -> {
                // 상승 - 초록색 (블룸버그 스타일)
                val green = (100 + (155 * intensity)).toInt()  // 100 ~ 255
                val red = (80 * (1 - intensity)).toInt()       // 80 ~ 0
                val blue = (60 * (1 - intensity)).toInt()      // 60 ~ 0
                Color(red, green, blue)
            }

            changeRate < 0 -> {
                // 하락 - 빨간색 (블룸버그 스타일)
                val red = (100 + (155 * intensity)).toInt()    // 100 ~ 255
                val green = (80 * (1 - intensity)).toInt()     // 80 ~ 0
                val blue = (60 * (1 - intensity)).toInt()      // 60 ~ 0
                Color(red, green, blue)
            }

            else -> {
                Color(102, 102, 102)
            }
        }
    }
}
