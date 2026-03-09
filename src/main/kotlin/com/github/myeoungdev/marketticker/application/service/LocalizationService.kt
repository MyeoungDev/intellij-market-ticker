package com.github.myeoungdev.marketticker.application.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.text.NumberFormat
import java.util.*

/**
 * UI 문자열 선택 및 숫자/퍼센트 로케일 포맷을 담당하는 서비스입니다.
 */
@Service(Service.Level.APP)
class LocalizationService {

    private val settingsService = service<AppSettingsService>()

    /**
     * 현재 UI 언어가 한국어인지 반환합니다.
     */
    fun isKorean(): Boolean {
        return currentLocale().language.equals("ko", ignoreCase = true)
    }

    /**
     * 현재 언어 설정에 맞게 한국어/영어 문자열 중 하나를 반환합니다.
     */
    fun text(ko: String, en: String): String {
        return if (isKorean()) ko else en
    }

    /**
     * 앱 설정을 기반으로 사용할 로케일을 계산합니다.
     */
    fun currentLocale(): Locale {
        return when (settingsService.getUiLanguage()) {
            AppSettingsService.UiLanguage.KO -> Locale.KOREA
            AppSettingsService.UiLanguage.EN -> Locale.US
            AppSettingsService.UiLanguage.AUTO -> Locale.getDefault()
        }
    }

    /**
     * 현재 로케일에 맞춰 숫자를 포맷합니다.
     */
    fun formatDecimal(value: Double, maxFractionDigits: Int = 2): String {
        val nf = NumberFormat.getNumberInstance(currentLocale())
        nf.maximumFractionDigits = maxFractionDigits
        nf.minimumFractionDigits = 0
        return nf.format(value)
    }

    /**
     * 현재 로케일에 맞춰 퍼센트 문자열을 생성합니다.
     */
    fun formatPercent(value: Double): String {
        return "${formatDecimal(value, 2)}%"
    }

    /**
     * 현재 로케일에 맞춰 퍼센트 문자열을 고정 소수점으로 생성합니다.
     */
    fun formatPercentFixed(value: Double, fractionDigits: Int = 2): String {
        val nf = NumberFormat.getNumberInstance(currentLocale())
        nf.maximumFractionDigits = fractionDigits
        nf.minimumFractionDigits = fractionDigits
        return "${nf.format(value)}%"
    }
}
