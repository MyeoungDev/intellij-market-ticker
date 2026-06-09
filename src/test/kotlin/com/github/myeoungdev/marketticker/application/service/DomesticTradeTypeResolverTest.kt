package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.DomesticTradeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class DomesticTradeTypeResolverTest {

    @Test
    fun `KRX 고정은 모든 시간에 KRX를 반환한다`() {
        val result = DomesticTradeTypeResolver.resolve(
            AppSettingsService.DomesticTradeVenueMode.KRX_ONLY,
            LocalTime.of(8, 0)
        )

        assertThat(result).isEqualTo(DomesticTradeType.KRX)
    }

    @Test
    fun `NXT 고정은 모든 시간에 NXT를 반환한다`() {
        val result = DomesticTradeTypeResolver.resolve(
            AppSettingsService.DomesticTradeVenueMode.NXT_ONLY,
            LocalTime.of(9, 30)
        )

        assertThat(result).isEqualTo(DomesticTradeType.NXT)
    }

    @Test
    fun `혼합 모드는 NXT 시작 시간부터 정규장 전까지 NXT를 반환한다`() {
        assertThat(resolveMixedAt(8, 0)).isEqualTo(DomesticTradeType.NXT)
        assertThat(resolveMixedAt(8, 59)).isEqualTo(DomesticTradeType.NXT)
    }

    @Test
    fun `혼합 모드는 정규장 시간에 KRX를 반환한다`() {
        assertThat(resolveMixedAt(9, 0)).isEqualTo(DomesticTradeType.KRX)
        assertThat(resolveMixedAt(15, 29)).isEqualTo(DomesticTradeType.KRX)
    }

    @Test
    fun `혼합 모드는 정규장 종료부터 NXT 종료 전까지 NXT를 반환한다`() {
        assertThat(resolveMixedAt(15, 30)).isEqualTo(DomesticTradeType.NXT)
        assertThat(resolveMixedAt(19, 59)).isEqualTo(DomesticTradeType.NXT)
    }

    @Test
    fun `혼합 모드는 NXT 거래 가능 시간 밖에서 KRX로 fallback 한다`() {
        assertThat(resolveMixedAt(7, 59)).isEqualTo(DomesticTradeType.KRX)
        assertThat(resolveMixedAt(20, 0)).isEqualTo(DomesticTradeType.KRX)
    }

    @Test
    fun `혼합 모드는 토요일과 일요일에 KRX로 fallback 한다`() {
        assertThat(resolveMixedAt(LocalDate.of(2026, 6, 13), 8, 30)).isEqualTo(DomesticTradeType.KRX)
        assertThat(resolveMixedAt(LocalDate.of(2026, 6, 14), 16, 0)).isEqualTo(DomesticTradeType.KRX)
    }

    @Test
    fun `NXT 고정도 주말에는 KRX로 fallback 한다`() {
        val result = DomesticTradeTypeResolver.resolve(
            AppSettingsService.DomesticTradeVenueMode.NXT_ONLY,
            LocalDate.of(2026, 6, 13),
            LocalTime.of(9, 30)
        )

        assertThat(result).isEqualTo(DomesticTradeType.KRX)
    }

    private fun resolveMixedAt(hour: Int, minute: Int): DomesticTradeType {
        return resolveMixedAt(LocalDate.of(2026, 6, 10), hour, minute)
    }

    private fun resolveMixedAt(date: LocalDate, hour: Int, minute: Int): DomesticTradeType {
        return DomesticTradeTypeResolver.resolve(
            AppSettingsService.DomesticTradeVenueMode.MIXED,
            date,
            LocalTime.of(hour, minute)
        )
    }
}
