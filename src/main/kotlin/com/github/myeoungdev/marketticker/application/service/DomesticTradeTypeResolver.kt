package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.DomesticTradeType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object DomesticTradeTypeResolver {
    val KOREA_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    private val NXT_OPEN: LocalTime = LocalTime.of(8, 0)
    private val KRX_OPEN: LocalTime = LocalTime.of(9, 0)
    private val KRX_CLOSE: LocalTime = LocalTime.of(15, 30)
    private val NXT_CLOSE: LocalTime = LocalTime.of(20, 0)

    fun resolve(mode: AppSettingsService.DomesticTradeVenueMode): DomesticTradeType {
        val now = ZonedDateTime.now(KOREA_ZONE)
        return resolve(mode, now.toLocalDate(), now.toLocalTime())
    }

    fun resolve(
        mode: AppSettingsService.DomesticTradeVenueMode,
        now: LocalTime
    ): DomesticTradeType {
        return resolve(mode, LocalDate.now(KOREA_ZONE), now)
    }

    fun resolve(
        mode: AppSettingsService.DomesticTradeVenueMode,
        date: LocalDate,
        now: LocalTime
    ): DomesticTradeType {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return DomesticTradeType.KRX
        }

        return when (mode) {
            AppSettingsService.DomesticTradeVenueMode.KRX_ONLY -> DomesticTradeType.KRX
            AppSettingsService.DomesticTradeVenueMode.NXT_ONLY -> DomesticTradeType.NXT
            AppSettingsService.DomesticTradeVenueMode.MIXED -> resolveMixed(date, now)
        }
    }

    private fun resolveMixed(date: LocalDate, now: LocalTime): DomesticTradeType {
        return when {
            !now.isBefore(KRX_OPEN) && now.isBefore(KRX_CLOSE) -> DomesticTradeType.KRX
            !now.isBefore(NXT_OPEN) && now.isBefore(NXT_CLOSE) -> DomesticTradeType.NXT
            else -> DomesticTradeType.KRX
        }
    }
}
