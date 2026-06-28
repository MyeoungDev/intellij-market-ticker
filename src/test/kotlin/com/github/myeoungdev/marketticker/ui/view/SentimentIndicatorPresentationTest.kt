package com.github.myeoungdev.marketticker.ui.view

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color

class SentimentIndicatorPresentationTest {

    @Test
    fun `sentiment presentation 은 score label 색상을 한 번에 계산한다`() {
        val presentation = presentSentimentIndicator(
            code = "FNG",
            score = 25.4571428571429,
            label = "공포",
            formatDecimal = { value, digits -> "%.${digits}f".format(value) }
        )

        assertThat(presentation.displayName).isEqualTo("Fear & Greed")
        assertThat(presentation.scoreText).isEqualTo("25.46 / 100")
        assertThat(presentation.labelText).isEqualTo("공포")
        assertThat(presentation.palette.background).isEqualTo(Color(57, 44, 28))
        assertThat(presentation.palette.border).isEqualTo(Color(166, 117, 58))
        assertThat(presentation.palette.accent).isEqualTo(Color(255, 206, 148))
    }

    @Test
    fun `label 이 없으면 N A 로 대체한다`() {
        val presentation = presentSentimentIndicator(
            code = "FNG",
            score = 10.0,
            label = null,
            formatDecimal = { value, digits -> "%.${digits}f".format(value) }
        )

        assertThat(presentation.labelText).isEqualTo("N/A")
    }

    @Test
    fun `blank label 은 N A 로 대체한다`() {
        val presentation = presentSentimentIndicator(
            code = "FNG",
            score = 10.0,
            label = "   ",
            formatDecimal = { value, digits -> "%.${digits}f".format(value) }
        )

        assertThat(presentation.labelText).isEqualTo("N/A")
    }

    @Test
    fun `score 경계값마다 다른 팔레트를 선택한다`() {
        val low = presentSentimentIndicator("FNG", 24.999999, "공포", ::format)
        val mid = presentSentimentIndicator("FNG", 25.0, "공포", ::format)
        val upperMid = presentSentimentIndicator("FNG", 45.0, "공포", ::format)
        val greed = presentSentimentIndicator("FNG", 75.0, "탐욕", ::format)

        assertThat(low.palette).isNotEqualTo(mid.palette)
        assertThat(mid.palette).isNotEqualTo(upperMid.palette)
        assertThat(upperMid.palette).isNotEqualTo(greed.palette)
    }

    @Test
    fun `이상치 score 는 중립 처리된다`() {
        val negative = presentSentimentIndicator("FNG", -1.0, "공포", ::format)
        val nan = presentSentimentIndicator("FNG", Double.NaN, "공포", ::format)
        val infinity = presentSentimentIndicator("FNG", Double.POSITIVE_INFINITY, "공포", ::format)

        assertThat(negative.scoreText).isEqualTo("N/A")
        assertThat(nan.scoreText).isEqualTo("N/A")
        assertThat(infinity.scoreText).isEqualTo("N/A")
        assertThat(negative.palette.background).isEqualTo(Color(45, 45, 45))
        assertThat(negative.palette.border).isEqualTo(Color(90, 90, 90))
        assertThat(negative.palette.accent).isEqualTo(Color(210, 210, 210))
        assertThat(nan.palette).isEqualTo(negative.palette)
        assertThat(infinity.palette).isEqualTo(negative.palette)
    }

    private fun format(value: Double, digits: Int): String = "%.${digits}f".format(value)
}
