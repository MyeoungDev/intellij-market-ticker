package com.github.myeoungdev.marketticker.application.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppSettingsServiceTest {

    @Test
    fun `고정 주기를 허용 범위 내로 저장한다`() {
        val service = AppSettingsService()

        service.setFixedIntervalSec(100)

        assertThat(service.getFixedIntervalSec()).isEqualTo(10)
    }

    @Test
    fun `자동 주기 open closed 값을 각각 저장한다`() {
        val service = AppSettingsService()

        service.setOpenIntervalSec(3)
        service.setClosedIntervalSec(10)

        assertThat(service.getOpenIntervalSec()).isEqualTo(3)
        assertThat(service.getClosedIntervalSec()).isEqualTo(10)
    }

    @Test
    fun `refresh mode와 ui language를 저장하고 조회한다`() {
        val service = AppSettingsService()

        service.setRefreshMode(AppSettingsService.RefreshMode.MANUAL)
        service.setUiLanguage(AppSettingsService.UiLanguage.EN)

        assertThat(service.getRefreshMode()).isEqualTo(AppSettingsService.RefreshMode.MANUAL)
        assertThat(service.getUiLanguage()).isEqualTo(AppSettingsService.UiLanguage.EN)
    }

    @Test
    fun `한 줄 지표 표시 여부를 저장하고 조회한다`() {
        val service = AppSettingsService()

        service.setMarketPulseVisible(false)

        assertThat(service.isMarketPulseVisible()).isFalse()
    }

    @Test
    fun `차트 탭과 히트맵 탭 표시 여부를 저장하고 조회한다`() {
        val service = AppSettingsService()

        service.setChartTabVisible(false)
        service.setHeatmapTabVisible(false)

        assertThat(service.isChartTabVisible()).isFalse()
        assertThat(service.isHeatmapTabVisible()).isFalse()
    }
}
