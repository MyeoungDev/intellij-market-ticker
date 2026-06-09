package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.CurrencyType
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
    fun `가격 표시 모드를 저장하고 조회한다`() {
        val service = AppSettingsService()

        service.setPriceDisplayMode(AppSettingsService.PriceDisplayMode.KRW_CONVERTED)

        assertThat(service.getPriceDisplayMode()).isEqualTo(AppSettingsService.PriceDisplayMode.KRW_CONVERTED)
    }

    @Test
    fun `가격 표시 모드 기본값은 혼용이다`() {
        val service = AppSettingsService()

        assertThat(service.getPriceDisplayMode()).isEqualTo(AppSettingsService.PriceDisplayMode.MIXED)
    }

    @Test
    fun `국내 주식 시세 기준 기본값은 KRX NXT 혼합이다`() {
        val service = AppSettingsService()

        assertThat(service.getDomesticTradeVenueMode()).isEqualTo(AppSettingsService.DomesticTradeVenueMode.MIXED)
    }

    @Test
    fun `국내 주식 시세 기준을 저장하고 조회한다`() {
        val service = AppSettingsService()

        service.setDomesticTradeVenueMode(AppSettingsService.DomesticTradeVenueMode.NXT_ONLY)

        assertThat(service.getDomesticTradeVenueMode()).isEqualTo(AppSettingsService.DomesticTradeVenueMode.NXT_ONLY)
    }

    @Test
    fun `국내 주식 시세 기준의 알 수 없는 저장값은 혼합으로 보정한다`() {
        val service = AppSettingsService()
        service.loadState(AppSettingsService.State(domesticTradeVenueMode = "INVALID"))

        assertThat(service.getDomesticTradeVenueMode()).isEqualTo(AppSettingsService.DomesticTradeVenueMode.MIXED)
    }

    @Test
    fun `기준 통화 기본값은 원화다`() {
        val service = AppSettingsService()

        assertThat(service.getBaseCurrency()).isEqualTo(CurrencyType.KRW)
    }

    @Test
    fun `기준 통화를 저장하고 조회한다`() {
        val service = AppSettingsService()

        service.setBaseCurrency(CurrencyType.USD)

        assertThat(service.getBaseCurrency()).isEqualTo(CurrencyType.USD)
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
