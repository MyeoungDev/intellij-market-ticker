package com.github.myeoungdev.marketticker.ui.view

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JLabel
import javax.swing.JComponent
import javax.swing.Timer

/**
 * 한 줄 시장 지표를 오른쪽에서 왼쪽으로 흐르게 표시하는 티커 컴포넌트입니다.
 */
class MarketPulseTicker : JComponent() {

    data class Chunk(
        val text: String,
        val color: Color,
        val bold: Boolean = false
    )

    private val chunks: MutableList<Chunk> = mutableListOf()
    private var offsetX: Int = 0
    private val streamGap = 40
    private val leftPadding = 12

    private val tickerTimer = Timer(24) {
        if (chunks.isEmpty() || width <= 0) return@Timer
        offsetX -= 1
        val streamWidth = measureStreamWidth()
        if (streamWidth <= 0) return@Timer
        if (offsetX + streamWidth + streamGap < 0) {
            offsetX += streamWidth + streamGap
        }
        repaint()
    }

    init {
        isOpaque = false
        font = JLabel().font.deriveFont(14f)
        preferredSize = Dimension(240, 32)
        minimumSize = Dimension(140, 30)
    }

    /**
     * 표시할 텍스트 조각 목록을 갱신합니다.
     */
    fun setChunks(newChunks: List<Chunk>) {
        chunks.clear()
        chunks.addAll(newChunks)
        // 즉시 텍스트가 보이도록 시작 위치를 컴포넌트 내부로 고정합니다.
        offsetX = leftPadding
        repaint()
    }

    override fun addNotify() {
        super.addNotify()
        if (!tickerTimer.isRunning) {
            tickerTimer.start()
        }
    }

    override fun removeNotify() {
        tickerTimer.stop()
        super.removeNotify()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (chunks.isEmpty()) return

        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val baseFont = font

        val streamWidth = measureStreamWidth(g2, baseFont)
        if (streamWidth <= 0) return
        val metrics = g2.getFontMetrics(baseFont)
        val y = ((height - metrics.height) / 2) + metrics.ascent

        // 화면을 꽉 채우도록 스트림을 반복 렌더링하여 좁은 폭에서도 항상 텍스트가 보이게 합니다.
        var x = offsetX
        while (x < width + streamWidth) {
            drawStream(g2, x, y, baseFont)
            x += streamWidth + streamGap
        }
    }

    private fun drawStream(g2: Graphics2D, startX: Int, baselineY: Int, baseFont: Font) {
        var x = startX
        chunks.forEach { chunk ->
            val font = if (chunk.bold) baseFont.deriveFont(Font.BOLD.toFloat()) else baseFont
            g2.font = font
            g2.color = chunk.color
            g2.drawString(chunk.text, x, baselineY)
            x += g2.fontMetrics.stringWidth(chunk.text)
        }
    }

    private fun measureStreamWidth(g2: Graphics2D, baseFont: Font): Int {
        return chunks.sumOf { chunk ->
            val font = if (chunk.bold) baseFont.deriveFont(Font.BOLD.toFloat()) else baseFont
            g2.font = font
            g2.fontMetrics.stringWidth(chunk.text)
        }
    }

    private fun measureStreamWidth(): Int {
        val metrics = getFontMetrics(font)
        return chunks.sumOf { chunk ->
            val f = if (chunk.bold) font.deriveFont(Font.BOLD.toFloat()) else font
            metrics.stringWidth(chunk.text).takeIf { f == font } ?: getFontMetrics(f).stringWidth(chunk.text)
        }
    }
}
