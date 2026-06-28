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
    val normalizedScore = score.takeIf { it.isFinite() && it in 0.0..100.0 }
    return SentimentIndicatorPresentation(
        displayName = when (normalizedCode) {
            "FNG", "FEAR & GREED" -> "Fear & Greed"
            else -> normalizedCode
        },
        scoreText = normalizedScore?.let { "${formatDecimal(it, 2)} / 100" } ?: "N/A",
        labelText = label?.takeIf { it.isNotBlank() } ?: "N/A",
        palette = sentimentPalette(normalizedScore)
    )
}

private fun sentimentPalette(score: Double?): SentimentIndicatorPalette {
    return when {
        score == null -> SentimentIndicatorPalette(Color(45, 45, 45), Color(90, 90, 90), Color(210, 210, 210))
        score < 25.0 -> SentimentIndicatorPalette(Color(56, 29, 29), Color(164, 68, 68), Color(246, 190, 190))
        score < 45.0 -> SentimentIndicatorPalette(Color(57, 44, 28), Color(166, 117, 58), Color(255, 206, 148))
        score < 55.0 -> SentimentIndicatorPalette(Color(55, 54, 28), Color(148, 141, 72), Color(248, 238, 158))
        score < 75.0 -> SentimentIndicatorPalette(Color(29, 53, 36), Color(72, 139, 86), Color(184, 228, 198))
        else -> SentimentIndicatorPalette(Color(27, 49, 48), Color(64, 148, 139), Color(178, 233, 229))
    }
}
