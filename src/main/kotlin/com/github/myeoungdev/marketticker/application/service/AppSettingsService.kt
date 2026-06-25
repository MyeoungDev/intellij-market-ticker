package com.github.myeoungdev.marketticker.application.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.github.myeoungdev.marketticker.domain.model.CurrencyType

/**
 * Market Ticker 전역 설정을 저장/조회하는 애플리케이션 서비스입니다.
 *
 * 업데이트 주기 모드와 언어 설정을 IDE 영속 저장소(`market_ticker_settings.xml`)에 보관합니다.
 */
@State(
    name = "MarketTickerSettings",
    storages = [Storage("market_ticker_settings.xml")]
)
@Service(Service.Level.APP)
class AppSettingsService : PersistentStateComponent<AppSettingsService.State> {

    companion object {
        const val MIN_INTERVAL_SEC: Long = 3L
        const val MAX_ACTIVE_INTERVAL_SEC: Long = 3600L
        const val DEFAULT_CLOSED_INTERVAL_SEC: Long = 300L
        const val MAX_CLOSED_INTERVAL_SEC: Long = 3600L
        const val DEFAULT_NEWS_PAGE_SIZE: Int = 15

        val ACTIVE_INTERVAL_OPTIONS: Array<Long> = arrayOf(
            3L,
            5L,
            6L,
            10L,
            15L,
            20L,
            30L,
            45L,
            60L,
            120L,
            180L,
            300L,
            600L,
            900L,
            1800L,
            3600L
        )
        val CLOSED_INTERVAL_OPTIONS: Array<Long> = arrayOf(
            30L,
            60L,
            180L,
            300L,
            600L,
            1800L,
            3600L
        )
        val NEWS_PAGE_SIZE_OPTIONS: Array<Int> = arrayOf(10, 15, 20, 30)

        fun formatPollingInterval(seconds: Long): String {
            return when {
                seconds >= 3600L && seconds % 3600L == 0L -> "${seconds / 3600L}h"
                seconds >= 60L && seconds % 60L == 0L -> "${seconds / 60L}m"
                else -> "${seconds}s"
            }
        }
    }

    /**
     * 가격 갱신 동작 모드입니다.
     */
    enum class RefreshMode {
        AUTO,
        FIXED,
        MANUAL;

        companion object {
            /**
             * 문자열 값을 [RefreshMode]로 변환합니다.
             */
            fun of(value: String): RefreshMode {
                return values().find { it.name == value } ?: AUTO
            }
        }
    }

    /**
     * UI 언어 설정입니다.
     */
    enum class UiLanguage {
        AUTO,
        KO,
        EN;

        companion object {
            /**
             * 문자열 값을 [UiLanguage]로 변환합니다.
             */
            fun of(value: String): UiLanguage {
                return values().find { it.name == value } ?: AUTO
            }
        }
    }

    /**
     * 금액성 값의 기본 표시 방식입니다.
     */
    enum class PriceDisplayMode {
        MIXED,
        KRW_CONVERTED;

        companion object {
            /**
             * 문자열 값을 [PriceDisplayMode]로 변환합니다.
             */
            fun of(value: String): PriceDisplayMode {
                return values().find { it.name == value } ?: MIXED
            }
        }
    }

    /**
     * 국내 주식 시세를 조회할 거래소 기준입니다.
     */
    enum class DomesticTradeVenueMode {
        KRX_ONLY,
        NXT_ONLY,
        MIXED;

        companion object {
            fun of(value: String): DomesticTradeVenueMode {
                return values().find { it.name == value } ?: MIXED
            }
        }
    }

    /**
     * 영속화 대상 설정 상태입니다.
     */
    data class State(
        var automaticPollingEnabled: Boolean = true,
        var refreshMode: String = RefreshMode.AUTO.name,
        var fixedIntervalSec: Long = 6L,
        var openIntervalSec: Long = 3L,
        var closedIntervalSec: Long = DEFAULT_CLOSED_INTERVAL_SEC,
        var uiLanguage: String = UiLanguage.AUTO.name,
        var priceDisplayMode: String = PriceDisplayMode.MIXED.name,
        var domesticTradeVenueMode: String = DomesticTradeVenueMode.MIXED.name,
        var baseCurrency: String = CurrencyType.KRW.code,
        var newsPageSize: Int = DEFAULT_NEWS_PAGE_SIZE,
        var showMarketPulse: Boolean = true,
        var showMarketSessionIndicator: Boolean = true,
        var showChartTab: Boolean = true,
        var showHeatmapTab: Boolean = true,
        var showPortfolioSummary: Boolean = true
    )

    private var settingsState = State()

    override fun getState(): State = settingsState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, settingsState)
    }

    fun isAutomaticPollingEnabled(): Boolean = settingsState.automaticPollingEnabled
    fun setAutomaticPollingEnabled(enabled: Boolean) {
        settingsState.automaticPollingEnabled = enabled
    }

    fun getRefreshMode(): RefreshMode = RefreshMode.of(settingsState.refreshMode)
    fun setRefreshMode(mode: RefreshMode) {
        settingsState.refreshMode = mode.name
    }

    fun getFixedIntervalSec(): Long = settingsState.fixedIntervalSec
    fun setFixedIntervalSec(value: Long) {
        settingsState.fixedIntervalSec = value.coerceIn(MIN_INTERVAL_SEC, MAX_ACTIVE_INTERVAL_SEC)
    }

    fun getOpenIntervalSec(): Long = settingsState.openIntervalSec
    fun setOpenIntervalSec(value: Long) {
        settingsState.openIntervalSec = value.coerceIn(MIN_INTERVAL_SEC, MAX_ACTIVE_INTERVAL_SEC)
    }

    fun getClosedIntervalSec(): Long = settingsState.closedIntervalSec
    fun setClosedIntervalSec(value: Long) {
        settingsState.closedIntervalSec = value.coerceIn(MIN_INTERVAL_SEC, MAX_CLOSED_INTERVAL_SEC)
    }

    fun getUiLanguage(): UiLanguage = UiLanguage.of(settingsState.uiLanguage)
    fun setUiLanguage(language: UiLanguage) {
        settingsState.uiLanguage = language.name
    }

    /**
     * 현재가/금액 표시 모드를 반환합니다.
     */
    fun getPriceDisplayMode(): PriceDisplayMode = PriceDisplayMode.of(settingsState.priceDisplayMode)

    /**
     * 현재가/금액 표시 모드를 저장합니다.
     */
    fun setPriceDisplayMode(mode: PriceDisplayMode) {
        settingsState.priceDisplayMode = mode.name
    }

    fun getDomesticTradeVenueMode(): DomesticTradeVenueMode {
        return DomesticTradeVenueMode.of(settingsState.domesticTradeVenueMode)
    }

    fun setDomesticTradeVenueMode(mode: DomesticTradeVenueMode) {
        settingsState.domesticTradeVenueMode = mode.name
    }

    /**
     * 환산 표시의 기준 통화를 반환합니다.
     */
    fun getBaseCurrency(): CurrencyType = CurrencyType.of(settingsState.baseCurrency).takeIf {
        it != CurrencyType.UNKNOWN
    } ?: CurrencyType.KRW

    /**
     * 환산 표시의 기준 통화를 저장합니다.
     */
    fun setBaseCurrency(currency: CurrencyType) {
        settingsState.baseCurrency = if (currency == CurrencyType.UNKNOWN) CurrencyType.KRW.code else currency.code
    }

    /**
     * 뉴스 탭과 기사 더보기의 기본 페이지 크기를 반환합니다.
     */
    fun getNewsPageSize(): Int = settingsState.newsPageSize.coerceIn(1, 50)

    /**
     * 뉴스 탭과 기사 더보기의 기본 페이지 크기를 저장합니다.
     */
    fun setNewsPageSize(value: Int) {
        settingsState.newsPageSize = value.coerceIn(1, 50)
    }

    /**
     * 한 줄 지표 표시 여부를 반환합니다.
     */
    fun isMarketPulseVisible(): Boolean = settingsState.showMarketPulse

    /**
     * 한 줄 지표 표시 여부를 저장합니다.
     */
    fun setMarketPulseVisible(visible: Boolean) {
        settingsState.showMarketPulse = visible
    }

    fun isMarketSessionIndicatorVisible(): Boolean = settingsState.showMarketSessionIndicator

    fun setMarketSessionIndicatorVisible(visible: Boolean) {
        settingsState.showMarketSessionIndicator = visible
    }

    fun isPortfolioSummaryVisible(): Boolean = settingsState.showPortfolioSummary

    fun setPortfolioSummaryVisible(visible: Boolean) {
        settingsState.showPortfolioSummary = visible
    }

    /**
     * 차트 탭 표시 여부를 반환합니다.
     */
    fun isChartTabVisible(): Boolean = settingsState.showChartTab

    /**
     * 차트 탭 표시 여부를 저장합니다.
     */
    fun setChartTabVisible(visible: Boolean) {
        settingsState.showChartTab = visible
    }

    /**
     * 히트맵 탭 표시 여부를 반환합니다.
     */
    fun isHeatmapTabVisible(): Boolean = settingsState.showHeatmapTab

    /**
     * 히트맵 탭 표시 여부를 저장합니다.
     */
    fun setHeatmapTabVisible(visible: Boolean) {
        settingsState.showHeatmapTab = visible
    }
}
