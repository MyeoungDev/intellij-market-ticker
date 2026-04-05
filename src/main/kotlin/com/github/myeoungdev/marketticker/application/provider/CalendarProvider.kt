package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.calendar.CalendarType
import com.github.myeoungdev.marketticker.domain.model.calendar.MarketCalendarEvent

/**
 * 캘린더 이벤트 데이터를 공급하는 provider 인터페이스입니다.
 */
interface CalendarProvider {

    fun getEvents(type: CalendarType, limit: Int = 50): List<MarketCalendarEvent>
}
