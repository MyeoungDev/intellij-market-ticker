package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.util.Locale
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.Dimension

/**
 * 사용자 설정을 메인 화면과 분리해 관리하는 모달 다이얼로그입니다.
 */
class SettingsDialog(
    private val onSaved: (() -> Unit)? = null
) : DialogWrapper(true) {

    private val settingsService = service<AppSettingsService>()
    private val localizationService = service<LocalizationService>()
    private val marketDataService = service<MarketDataService>()

    private val refreshModeCombo = JComboBox(AppSettingsService.RefreshMode.values())
    private val fixedIntervalCombo = JComboBox(arrayOf(3L, 6L, 10L))
    private val openIntervalCombo = JComboBox(arrayOf(3L, 6L, 10L))
    private val closedIntervalCombo = JComboBox(arrayOf(3L, 6L, 10L))
    private val languageCombo = JComboBox(AppSettingsService.UiLanguage.values())
    private val marketPulseCheckBox = JCheckBox()

    init {
        title = localizationService.text("Market Ticker 설정", "Market Ticker Settings")

        refreshModeCombo.selectedItem = settingsService.getRefreshMode()
        fixedIntervalCombo.selectedItem = settingsService.getFixedIntervalSec()
        openIntervalCombo.selectedItem = settingsService.getOpenIntervalSec()
        closedIntervalCombo.selectedItem = settingsService.getClosedIntervalSec()
        languageCombo.selectedItem = settingsService.getUiLanguage()
        marketPulseCheckBox.isSelected = settingsService.isMarketPulseVisible()

        refreshModeCombo.addActionListener {
            updateIntervalControlAvailability()
            persistSettings()
        }
        fixedIntervalCombo.addActionListener { persistSettings() }
        openIntervalCombo.addActionListener { persistSettings() }
        closedIntervalCombo.addActionListener { persistSettings() }
        languageCombo.addActionListener { persistSettings() }
        marketPulseCheckBox.addActionListener { persistSettings() }

        updateIntervalControlAvailability()
        init()
        setSize(560, 380)
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row(localizationService.text("업데이트 모드", "Refresh mode")) {
                cell(refreshModeCombo).align(AlignX.FILL)
            }

            row(localizationService.text("고정 주기(초)", "Fixed interval (sec)")) {
                cell(fixedIntervalCombo).align(AlignX.FILL)
            }

            row(localizationService.text("장중 주기(초)", "Open market interval (sec)")) {
                cell(openIntervalCombo).align(AlignX.FILL)
            }

            row(localizationService.text("비장중 주기(초)", "Closed market interval (sec)")) {
                cell(closedIntervalCombo).align(AlignX.FILL)
            }

            row(localizationService.text("언어", "Language")) {
                cell(languageCombo).align(AlignX.FILL)
            }

            row {
                marketPulseCheckBox.text = localizationService.text("상단 한 줄 지표 표시", "Show top market pulse")
                cell(marketPulseCheckBox)
            }

            row {
                button(localizationService.text("지금 새로고침", "Refresh now")) {
                    marketDataService.forceRefresh()
                }
            }
        }.apply {
            preferredSize = Dimension(520, 300)
        }
    }

    override fun doOKAction() {
        persistSettings()
        onSaved?.invoke()
        super.doOKAction()
    }

    private fun persistSettings() {
        val mode = refreshModeCombo.selectedItem as? AppSettingsService.RefreshMode ?: AppSettingsService.RefreshMode.AUTO
        val fixed = fixedIntervalCombo.selectedItem as? Long ?: 6L
        val open = openIntervalCombo.selectedItem as? Long ?: 3L
        val closed = closedIntervalCombo.selectedItem as? Long ?: 10L
        val language = languageCombo.selectedItem as? AppSettingsService.UiLanguage ?: AppSettingsService.UiLanguage.AUTO

        settingsService.setRefreshMode(mode)
        settingsService.setFixedIntervalSec(fixed)
        settingsService.setOpenIntervalSec(open)
        settingsService.setClosedIntervalSec(closed)
        settingsService.setUiLanguage(language)
        settingsService.setMarketPulseVisible(marketPulseCheckBox.isSelected)

        Locale.setDefault(localizationService.currentLocale())
    }

    private fun updateIntervalControlAvailability() {
        val mode = refreshModeCombo.selectedItem as? AppSettingsService.RefreshMode ?: AppSettingsService.RefreshMode.AUTO
        fixedIntervalCombo.isEnabled = mode == AppSettingsService.RefreshMode.FIXED
        openIntervalCombo.isEnabled = mode == AppSettingsService.RefreshMode.AUTO
        closedIntervalCombo.isEnabled = mode == AppSettingsService.RefreshMode.AUTO
    }
}
