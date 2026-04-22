package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.ChartDataService
import com.github.myeoungdev.marketticker.application.service.PriceHistoryService
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import kotlinx.coroutines.*
import java.awt.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 선택된 종목의 히스토리 데이터를 캔들/거래량/이동평균으로 렌더링하는 뷰입니다.
 */
class ChartView : JPanel(BorderLayout()) {

    private val historyService = service<PriceHistoryService>()
    private val chartDataService = service<ChartDataService>()
    private val localizationService = service<LocalizationService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var selectedTicker: Ticker? = null
    private var period: PriceHistoryService.Period = PriceHistoryService.Period.DAY
    private var candles: List<PriceHistoryService.Candle> = emptyList()
    private var ma5: List<Double?> = emptyList()
    private var ma20: List<Double?> = emptyList()

    private val titleLabel = JLabel(localizationService.text("차트: 종목을 선택하세요", "Chart: select a symbol"))
    private val periodCombo = JComboBox(PriceHistoryService.Period.values())
    private val canvas = ChartCanvas()

    init {
        val topPanel = JPanel(BorderLayout())
        topPanel.add(titleLabel, BorderLayout.WEST)
        topPanel.add(periodCombo, BorderLayout.EAST)

        periodCombo.addActionListener {
            val selected = periodCombo.selectedItem as? PriceHistoryService.Period ?: PriceHistoryService.Period.DAY
            period = selected
            refreshChart()
        }

        add(topPanel, BorderLayout.NORTH)
        add(canvas, BorderLayout.CENTER)
    }

    /**
     * 차트 대상 종목을 변경하고 즉시 갱신합니다.
     */
    fun updateSelection(ticker: Ticker) {
        selectedTicker = ticker
        titleLabel.text = localizationService.text("차트", "Chart") + ": ${ticker.name} (${ticker.symbol})"
        refreshChart()
    }

    /**
     * 현재 선택된 종목과 기간 기준으로 차트 데이터를 다시 계산합니다.
     */
    fun refreshChart() {
        val ticker = selectedTicker ?: return

        scope.launch {
            val data = chartDataService.loadCandles(ticker, period)

            ApplicationManager.getApplication().invokeLater {
                candles = data.takeLast(visibleCandleLimit())
                ma5 = historyService.movingAverage(candles, 5)
                ma20 = historyService.movingAverage(candles, 20)
                canvas.repaint()
            }
        }
    }

    override fun removeNotify() {
        super.removeNotify()
        scope.cancel()
    }

    /**
     * 기간별 표시 캔들 수를 제한해 차트 밀도를 유지합니다.
     */
    private fun visibleCandleLimit(): Int = when (period) {
        PriceHistoryService.Period.DAY -> 30
        PriceHistoryService.Period.WEEK -> 45
        PriceHistoryService.Period.MONTH -> 60
        PriceHistoryService.Period.YEAR -> 80
    }

    private inner class ChartCanvas : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val width = width
            val height = height

            g2.color = Color(36, 36, 36)
            g2.fillRect(0, 0, width, height)

            if (candles.isEmpty()) {
                g2.color = Color.LIGHT_GRAY
                g2.drawString(localizationService.text("차트 데이터가 없습니다.", "No chart data yet."), 16, 24)
                return
            }

            val topPadding = 16
            val bottomPadding = 88
            val leftPadding = 40
            val rightPadding = 20

            val chartTop = topPadding
            val chartBottom = height - bottomPadding
            val chartHeight = chartBottom - chartTop
            val chartWidth = width - leftPadding - rightPadding

            val maxPrice = candles.maxOf { it.high }
            val minPrice = candles.minOf { it.low }
            val priceRange = (maxPrice - minPrice).coerceAtLeast(0.0001)

            val maxVolume = candles.maxOf { it.volume }.coerceAtLeast(1)
            val candleWidth = (chartWidth / candles.size.toDouble()).coerceAtLeast(3.0)
            val zoneId = selectedTicker?.marketType?.zoneId ?: ZoneId.systemDefault()

            candles.forEachIndexed { index, candle ->
                val x = (leftPadding + index * candleWidth).toInt()
                val centerX = (x + candleWidth / 2).toInt()

                fun y(price: Double): Int {
                    return (chartBottom - ((price - minPrice) / priceRange) * chartHeight).toInt()
                }

                val openY = y(candle.open)
                val closeY = y(candle.close)
                val highY = y(candle.high)
                val lowY = y(candle.low)

                val isUp = candle.close >= candle.open
                g2.color = if (isUp) Color(244, 67, 54) else Color(66, 133, 244)

                g2.drawLine(centerX, highY, centerX, lowY)

                val bodyTop = minOf(openY, closeY)
                val bodyHeight = kotlin.math.abs(closeY - openY).coerceAtLeast(2)
                g2.fillRect(x + 1, bodyTop, candleWidth.toInt().coerceAtLeast(2) - 2, bodyHeight)

                val volumeTop = height - 58
                val volumeHeight = ((candle.volume.toDouble() / maxVolume) * 28.0).toInt().coerceAtLeast(1)
                g2.color = if (isUp) Color(255, 138, 128) else Color(130, 177, 255)
                g2.fillRect(
                    x + 1,
                    volumeTop + (28 - volumeHeight),
                    candleWidth.toInt().coerceAtLeast(2) - 2,
                    volumeHeight
                )
            }

            drawMa(
                g2,
                ma5,
                candles,
                leftPadding,
                candleWidth,
                chartBottom,
                minPrice,
                priceRange,
                chartHeight,
                Color(255, 193, 7)
            )
            drawMa(
                g2,
                ma20,
                candles,
                leftPadding,
                candleWidth,
                chartBottom,
                minPrice,
                priceRange,
                chartHeight,
                Color(0, 230, 118)
            )

            drawAxisLabels(g2, leftPadding, rightPadding, chartBottom, height, maxPrice, minPrice, zoneId)

            g2.color = Color(200, 200, 200)
            g2.drawString("MA5", leftPadding, height - 30)
            g2.drawString("MA20", leftPadding + 60, height - 30)
            g2.color = Color(255, 193, 7)
            g2.fillRect(leftPadding + 32, height - 36, 20, 4)
            g2.color = Color(0, 230, 118)
            g2.fillRect(leftPadding + 100, height - 36, 20, 4)

            g2.color = Color(180, 180, 180)
            g2.drawString(localizationService.text("거래량", "Volume"), leftPadding, height - 64)
        }

        private fun drawAxisLabels(
            g2: Graphics2D,
            leftPadding: Int,
            rightPadding: Int,
            chartBottom: Int,
            canvasHeight: Int,
            maxPrice: Double,
            minPrice: Double,
            zoneId: ZoneId
        ) {
            g2.color = Color(160, 160, 160)
            g2.drawString(localizationService.formatDecimal(maxPrice, 2), 8, 24)
            g2.drawString(localizationService.formatDecimal(minPrice, 2), 8, chartBottom)

            if (candles.size < 2) return

            val first = candles.first().at.atZone(zoneId)
            val mid = candles[candles.size / 2].at.atZone(zoneId)
            val last = candles.last().at.atZone(zoneId)
            val fmt = if (period == PriceHistoryService.Period.DAY) {
                DateTimeFormatter.ofPattern("MM-dd HH:mm")
            } else {
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
            }

            val y = canvasHeight - 8
            val chartWidth = width - leftPadding - rightPadding
            val xStart = leftPadding
            val xMid = leftPadding + (chartWidth / 2)
            val xEnd = leftPadding + chartWidth - 96

            g2.drawString(first.format(fmt), xStart, y)
            g2.drawString(mid.format(fmt), xMid, y)
            g2.drawString(last.format(fmt), xEnd.coerceAtLeast(xMid + 40), y)
        }

        private fun drawMa(
            g2: Graphics2D,
            ma: List<Double?>,
            candles: List<PriceHistoryService.Candle>,
            leftPadding: Int,
            candleWidth: Double,
            chartBottom: Int,
            minPrice: Double,
            priceRange: Double,
            chartHeight: Int,
            color: Color
        ) {
            g2.color = color
            g2.stroke = BasicStroke(1.8f)

            var prevX: Int? = null
            var prevY: Int? = null

            ma.forEachIndexed { index, value ->
                if (value == null) return@forEachIndexed
                if (index >= candles.size) return@forEachIndexed

                val x = (leftPadding + index * candleWidth + candleWidth / 2).toInt()
                val y = (chartBottom - ((value - minPrice) / priceRange) * chartHeight).toInt()

                if (prevX != null && prevY != null) {
                    g2.drawLine(prevX!!, prevY!!, x, y)
                }
                prevX = x
                prevY = y
            }
        }
    }
}
