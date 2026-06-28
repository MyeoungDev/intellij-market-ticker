package com.github.myeoungdev.marketticker.ui.view

import java.awt.Color

internal data class SentimentIndicatorPresentation(
    val displayName: String,
    val scoreText: String,
    val labelText: String,
    val palette: SentimentIndicatorPalette
)

internal data class SentimentIndicatorPalette(
    val background: Color,
    val border: Color,
    val accent: Color
)

internal fun presentSentimentIndicator(
    code: String,
    score: Double,
    label: String?,
    formatDecimal: (Double, Int) -> String
): SentimentIndicatorPresentation {
    val normalizedCode = code.uppercase()
    return SentimentIndicatorPresentation(
        displayName = when (normalizedCode) {
            "FNG", "FEAR & GREED" -> "Fear & Greed"
            else -> normalizedCode
        },
        scoreText = "${formatDecimal(score, 2)} / 100",
        labelText = label?.takeIf { it.isNotBlank() } ?: "N/A",
        palette = sentimentPalette(score)
    )
}

private fun sentimentPalette(score: Double): SentimentIndicatorPalette {
    return when (score) {
        in 0.0..24.999999 -> SentimentIndicatorPalette(Color(56, 29, 29), Color(164, 68, 68), Color(246, 190, 190))
        in 25.0..44.999999 -> SentimentIndicatorPalette(Color(57, 44, 28), Color(166, 117, 58), Color(255, 206, 148))
        in 45.0..54.999999 -> SentimentIndicatorPalette(Color(55, 54, 28), Color(148, 141, 72), Color(248, 238, 158))
        in 55.0..74.999999 -> SentimentIndicatorPalette(Color(29, 53, 36), Color(72, 139, 86), Color(184, 228, 198))
        else -> SentimentIndicatorPalette(Color(27, 49, 48), Color(64, 148, 139), Color(178, 233, 229))
    }
}
