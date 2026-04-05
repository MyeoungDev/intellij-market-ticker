package com.github.myeoungdev.marketticker.infrastructure.finviz

import com.github.myeoungdev.marketticker.application.provider.CalendarProvider
import com.github.myeoungdev.marketticker.domain.model.calendar.CalendarType
import com.github.myeoungdev.marketticker.domain.model.calendar.MarketCalendarEvent

class FinvizCalendarProvider(
    private val client: FinvizClient
) : CalendarProvider {

    override fun getEvents(type: CalendarType, limit: Int): List<MarketCalendarEvent> {
        return client.fetchCalendar(type, limit)
    }
}
