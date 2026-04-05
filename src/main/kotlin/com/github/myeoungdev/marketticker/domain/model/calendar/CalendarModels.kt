package com.github.myeoungdev.marketticker.domain.model.calendar

enum class CalendarType(
    val path: String,
    val labelKo: String,
    val labelEn: String
) {
    EARNINGS("earnings", "실적", "Earnings"),
    ECONOMIC("economic", "경제지표", "Economic");
}

data class MarketCalendarEvent(
    val type: CalendarType,
    val dateTime: String,
    val ticker: String?,
    val title: String,
    val subtitle: String,
    val actual: String,
    val forecast: String,
    val previous: String,
    val impact: Int
)
