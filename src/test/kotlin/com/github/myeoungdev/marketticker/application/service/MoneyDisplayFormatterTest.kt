package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class MoneyDisplayFormatterTest {

    @Test
    fun `혼용 모드에서는 원문 통화로 표시한다`() {
        val settings = AppSettingsService()
        settings.setPriceDisplayMode(AppSettingsService.PriceDisplayMode.MIXED)
        val formatter = MoneyDisplayFormatter(
            settingsService = settings,
            localeProvider = { Locale.US }
        )

        assertThat(formatter.formatAmount(195.12, CurrencyType.USD)).isEqualTo("195.12 USD")
        assertThat(formatter.formatAmount(72_000.0, CurrencyType.KRW)).isEqualTo("72,000 KRW")
    }

    @Test
    fun `원화 환산 모드에서는 FX 기준으로 KRW로 변환한다`() {
        val settings = AppSettingsService()
        settings.setPriceDisplayMode(AppSettingsService.PriceDisplayMode.KRW_CONVERTED)
        val formatter = MoneyDisplayFormatter(
            settingsService = settings,
            localeProvider = { Locale.US },
            exchangeRateProvider = {
                listOf(
                    fxIndicator("FX_USDKRW", 1_477.60),
                    fxIndicator("FX_JPYKRW", 927.59),
                    fxIndicator("FX_EURKRW", 1_734.85),
                    fxIndicator("FX_CNYKRW", 216.49),
                    fxIndicator("FX_HKDKRW", 189.14)
                )
            }
        )

        assertThat(formatter.formatAmount(195.12, CurrencyType.USD)).isEqualTo("288,309.31 KRW")
        assertThat(formatter.formatAmount(1_000.0, CurrencyType.JPY)).isEqualTo("9,275.9 KRW")
        assertThat(formatter.formatAmount(100.0, CurrencyType.HKD)).isEqualTo("18,914 KRW")
    }

    @Test
    fun `기준 통화가 달라도 같은 규칙으로 환산한다`() {
        val settings = AppSettingsService()
        settings.setPriceDisplayMode(AppSettingsService.PriceDisplayMode.KRW_CONVERTED)
        settings.setBaseCurrency(CurrencyType.USD)
        val formatter = MoneyDisplayFormatter(
            settingsService = settings,
            localeProvider = { Locale.US },
            exchangeRateProvider = {
                listOf(
                    fxIndicator("FX_USDKRW", 1_477.60),
                    fxIndicator("FX_JPYKRW", 927.59),
                    fxIndicator("FX_EURKRW", 1_734.85),
                    fxIndicator("FX_CNYKRW", 216.49),
                    fxIndicator("FX_HKDKRW", 189.14)
                )
            }
        )

        assertThat(formatter.formatAmount(72_000.0, CurrencyType.KRW)).isEqualTo("48.73 USD")
    }

    @Test
    fun `환율이 없으면 원문 표시로 폴백한다`() {
        val settings = AppSettingsService()
        settings.setPriceDisplayMode(AppSettingsService.PriceDisplayMode.KRW_CONVERTED)
        val formatter = MoneyDisplayFormatter(
            settingsService = settings,
            localeProvider = { Locale.US },
            exchangeRateProvider = { emptyList() }
        )

        assertThat(formatter.formatAmount(195.12, CurrencyType.USD)).isEqualTo("195.12 USD")
        assertThat(formatter.formatAmount(195.12, null)).isEqualTo("195.12")
    }

    @Test
    fun `signed amount 포맷은 부호를 유지한다`() {
        val settings = AppSettingsService()
        settings.setPriceDisplayMode(AppSettingsService.PriceDisplayMode.MIXED)
        val formatter = MoneyDisplayFormatter(
            settingsService = settings,
            localeProvider = { Locale.US }
        )

        assertThat(formatter.formatSignedAmount(12.34, CurrencyType.USD)).isEqualTo("+12.34 USD")
        assertThat(formatter.formatSignedAmount(-12.34, CurrencyType.USD)).isEqualTo("-12.34 USD")
    }

    private fun fxIndicator(code: String, price: Double): MarketIndicator {
        return MarketIndicator(
            code = code,
            name = code,
            currentPrice = price,
            changeRate = 0.0,
            marketStatus = MarketStatus.OPEN,
            category = IndicatorCategory.EXCHANGE_RATE,
            unit = "KRW"
        )
    }
}
