package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppSettingsServiceTest {

    @Test
    fun `고정 주기를 허용 범위 내로 저장한다`() {
        val service = AppSettingsService()

        service.setFixedIntervalSec(3600)

        assertThat(service.getFixedIntervalSec()).isEqualTo(3600)
    }

    @Test
    fun `고정 주기는 장중 최대 범위로 제한한다`() {
        val service = AppSettingsService()

        service.setFixedIntervalSec(7200)

        assertThat(service.getFixedIntervalSec()).isEqualTo(3600)
    }

    @Test
    fun `자동 주기 open closed 값을 각각 저장한다`() {
        val service = AppSettingsService()

        service.setOpenIntervalSec(1800)
        service.setClosedIntervalSec(300)

        assertThat(service.getOpenIntervalSec()).isEqualTo(1800)
        assertThat(service.getClosedIntervalSec()).isEqualTo(300)
    }

    @Test
    fun `장중 주기는 장중 최대 범위로 제한한다`() {
        val service = AppSettingsService()

        service.setOpenIntervalSec(7200)

        assertThat(service.getOpenIntervalSec()).isEqualTo(3600)
    }

    @Test
    fun `비장중 주기는 1시간 최대 범위로 제한한다`() {
        val service = AppSettingsService()

        service.setClosedIntervalSec(7200)

        assertThat(service.getClosedIntervalSec()).isEqualTo(3600)
    }

    @Test
    fun `폴링 주기 표시 문자열을 초 분 시간 단위로 변환한다`() {
        assertThat(AppSettingsService.formatPollingInterval(30)).isEqualTo("30s")
        assertThat(AppSettingsService.formatPollingInterval(300)).isEqualTo("5m")
        assertThat(AppSettingsService.formatPollingInterval(3600)).isEqualTo("1h")
    }

    @Test
    fun `뉴스 페이지 크기 기본값은 15다`() {
        val service = AppSettingsService()

        assertThat(service.getNewsPageSize()).isEqualTo(AppSettingsService.DEFAULT_NEWS_PAGE_SIZE)
    }

    @Test
    fun `뉴스 페이지 크기를 저장하고 조회한다`() {
        val service = AppSettingsService()

        service.setNewsPageSize(20)

        assertThat(service.getNewsPageSize()).isEqualTo(20)
    }

    @Test
    fun `뉴스 페이지 크기는 저장 상태에서 복원된다`() {
        val service = AppSettingsService()

        service.loadState(AppSettingsService.State(newsPageSize = 30))

        assertThat(service.getNewsPageSize()).isEqualTo(30)
    }

    @Test
    fun `장중 폴링 주기 옵션은 중간 초와 분 단위 선택지를 포함한다`() {
        assertThat(AppSettingsService.ACTIVE_INTERVAL_OPTIONS).contains(
            15L,
            20L,
            45L,
            120L,
            180L,
            900L
        )
    }

    @Test
    fun `비장중 폴링 주기 옵션은 긴 대기 선택지를 포함한다`() {
        assertThat(AppSettingsService.CLOSED_INTERVAL_OPTIONS).containsExactly(
            30L,
            60L,
            180L,
            300L,
            600L,
            1800L,
            3600L
        )
    }

    @Test
    fun `비장중 폴링 주기 기본값은 5분이다`() {
        val service = AppSettingsService()

        assertThat(service.getClosedIntervalSec()).isEqualTo(AppSettingsService.DEFAULT_CLOSED_INTERVAL_SEC)
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
    fun `자동 폴링은 기본으로 활성화되어 있다`() {
        val service = AppSettingsService()

        assertThat(service.isAutomaticPollingEnabled()).isTrue()
    }

    @Test
    fun `자동 폴링 활성 상태를 저장하고 조회한다`() {
        val service = AppSettingsService()

        service.setAutomaticPollingEnabled(false)

        assertThat(service.isAutomaticPollingEnabled()).isFalse()
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
    fun `포트폴리오 요약 표시는 기본으로 활성화되어 있다`() {
        val service = AppSettingsService()

        assertThat(service.isPortfolioSummaryVisible()).isTrue()
    }

    @Test
    fun `포트폴리오 요약 표시 여부를 저장하고 조회한다`() {
        val service = AppSettingsService()

        service.setPortfolioSummaryVisible(false)

        assertThat(service.isPortfolioSummaryVisible()).isFalse()
    }

    @Test
    fun `포트폴리오 요약 표시 여부는 저장 상태에서 복원된다`() {
        val service = AppSettingsService()

        service.loadState(AppSettingsService.State(showPortfolioSummary = false))

        assertThat(service.isPortfolioSummaryVisible()).isFalse()
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
