package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.common.extenion.withCurrencyCode
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.intellij.openapi.components.service
import io.github.oshai.kotlinlogging.KotlinLogging
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

/**
 * 현재가 및 금액성 값을 화면 규칙에 맞춰 포맷하고 필요 시 선택된 기준 통화로 환산하는 헬퍼입니다.
 */
class MoneyDisplayFormatter(
    private val settingsService: AppSettingsService = service(),
    private val localeProvider: () -> Locale = {
        when (settingsService.getUiLanguage()) {
            AppSettingsService.UiLanguage.KO -> Locale.KOREA
            AppSettingsService.UiLanguage.EN -> Locale.US
            AppSettingsService.UiLanguage.AUTO -> Locale.getDefault()
        }
    },
    private val exchangeRateProvider: () -> List<MarketIndicator> = {
        service<MarketIndicatorService>().indicators.value
    }
) {

    fun formatAmount(value: Double?, currency: CurrencyType?, maxFractionDigits: Int = 2): String {
        if (value == null || !value.isFinite()) {
            return "-"
        }

        return when (settingsService.getPriceDisplayMode()) {
            AppSettingsService.PriceDisplayMode.MIXED -> formatNative(value, currency, maxFractionDigits)
            AppSettingsService.PriceDisplayMode.KRW_CONVERTED -> {
                val converted = convertToBaseCurrency(value, currency, settingsService.getBaseCurrency())
                if (converted == null) {
                    formatNative(value, currency, maxFractionDigits)
                } else {
                    formatNative(converted, settingsService.getBaseCurrency(), maxFractionDigits)
                }
            }
        }
    }

    fun formatSignedAmount(value: Double?, currency: CurrencyType?, maxFractionDigits: Int = 2): String {
        if (value == null || !value.isFinite()) {
            return "-"
        }

        val formatted = formatAmount(abs(value), currency, maxFractionDigits)
        return when {
            value > 0 -> "+$formatted"
            value < 0 -> "-$formatted"
            else -> formatted
        }
    }

    fun formatNativeAmount(value: Double?, currency: CurrencyType?, maxFractionDigits: Int = 2): String {
        if (value == null || !value.isFinite()) {
            return "-"
        }

        return formatNative(value, currency, maxFractionDigits)
    }

    fun formatNativeSignedAmount(value: Double?, currency: CurrencyType?, maxFractionDigits: Int = 2): String {
        if (value == null || !value.isFinite()) {
            return "-"
        }

        val formatted = formatNative(abs(value), currency, maxFractionDigits)
        return when {
            value > 0 -> "+$formatted"
            value < 0 -> "-$formatted"
            else -> formatted
        }
    }

    private fun formatNative(value: Double, currency: CurrencyType?, maxFractionDigits: Int): String {
        val formatted = formatDecimal(value, maxFractionDigits)
        return formatted.withCurrencyCode(currency)
    }

    fun convertToBaseCurrency(
        value: Double?,
        currency: CurrencyType?,
        baseCurrency: CurrencyType = settingsService.getBaseCurrency()
    ): Double? {
        if (value == null || !value.isFinite() || currency == null || currency == CurrencyType.UNKNOWN) {
            return null
        }

        if (currency == baseCurrency) {
            return value
        }

        val sourceRate = resolveKrwRate(currency)
        val baseRate = resolveKrwRate(baseCurrency)
        if (sourceRate == null || sourceRate <= 0.0 || baseRate == null || baseRate <= 0.0) {
            logger.debug { "No exchange rate available for ${currency.code} -> ${baseCurrency.code}" }
            return null
        }

        val converted = value * (sourceRate / baseRate)
        return if (converted.isFinite()) converted else null
    }

    private fun resolveKrwRate(currency: CurrencyType): Double? {
        if (currency == CurrencyType.KRW) {
            return 1.0
        }

        val rateCode = when (currency) {
            CurrencyType.USD -> "FX_USDKRW"
            CurrencyType.JPY -> "FX_JPYKRW"
            CurrencyType.EUR -> "FX_EURKRW"
            CurrencyType.CNY -> "FX_CNYKRW"
            CurrencyType.HKD -> "FX_HKDKRW"
            else -> null
        } ?: return null

        return exchangeRateProvider()
            .firstOrNull { it.code.equals(rateCode, ignoreCase = true) }
            ?.currentPrice
            ?.takeIf { it > 0.0 }
            ?.let { rate ->
                when (currency) {
                    CurrencyType.JPY -> rate / 100.0
                    else -> rate
                }
            }
    }

    private fun formatDecimal(value: Double, maxFractionDigits: Int): String {
        val format = NumberFormat.getNumberInstance(localeProvider())
        format.maximumFractionDigits = maxFractionDigits
        format.minimumFractionDigits = 0
        return format.format(value)
    }

}
