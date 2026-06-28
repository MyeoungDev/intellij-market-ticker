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
}
