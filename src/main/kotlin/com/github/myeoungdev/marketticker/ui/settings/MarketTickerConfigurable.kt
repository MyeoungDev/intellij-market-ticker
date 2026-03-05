package com.github.myeoungdev.marketticker.ui.settings

import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.util.Locale
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent

/**
 * IntelliJ Settings 에서 Market Ticker 설정을 제공하는 Configurable 입니다.
 */
class MarketTickerConfigurable : Configurable {

    private val settingsService = service<AppSettingsService>()
    private val localizationService = service<LocalizationService>()

    private val refreshModeCombo = JComboBox(AppSettingsService.RefreshMode.values())
    private val fixedIntervalCombo = JComboBox(arrayOf(3L, 6L, 10L))
    private val openIntervalCombo = JComboBox(arrayOf(3L, 6L, 10L))
    private val closedIntervalCombo = JComboBox(arrayOf(3L, 6L, 10L))
    private val languageCombo = JComboBox(AppSettingsService.UiLanguage.values())
    private val marketPulseCheckBox = JCheckBox()
    private val chartTabCheckBox = JCheckBox()
    private val heatmapTabCheckBox = JCheckBox()

    private var component: JComponent? = null

    override fun getDisplayName(): String = "Market Ticker"

    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent {
        if (component == null) {
            refreshModeCombo.addActionListener { updateIntervalControlAvailability() }
            component = panel {
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
                    marketPulseCheckBox.text = localizationService.text("한 줄 지표 표시", "Show market pulse ticker")
                    cell(marketPulseCheckBox)
                }

                row {
                    chartTabCheckBox.text = localizationService.text("차트 탭 표시", "Show chart tab")
                    cell(chartTabCheckBox)
                }

                row {
                    heatmapTabCheckBox.text = localizationService.text("히트맵 탭 표시", "Show heatmap tab")
                    cell(heatmapTabCheckBox)
                }
            }
        }

        reset()
        return component!!
    }

    override fun isModified(): Boolean {
        val mode = refreshModeCombo.selectedItem as? AppSettingsService.RefreshMode ?: AppSettingsService.RefreshMode.AUTO
        val fixed = fixedIntervalCombo.selectedItem as? Long ?: 6L
        val open = openIntervalCombo.selectedItem as? Long ?: 3L
        val closed = closedIntervalCombo.selectedItem as? Long ?: 10L
        val language = languageCombo.selectedItem as? AppSettingsService.UiLanguage ?: AppSettingsService.UiLanguage.AUTO

        return mode != settingsService.getRefreshMode() ||
                fixed != settingsService.getFixedIntervalSec() ||
                open != settingsService.getOpenIntervalSec() ||
                closed != settingsService.getClosedIntervalSec() ||
                language != settingsService.getUiLanguage() ||
                marketPulseCheckBox.isSelected != settingsService.isMarketPulseVisible() ||
                chartTabCheckBox.isSelected != settingsService.isChartTabVisible() ||
                heatmapTabCheckBox.isSelected != settingsService.isHeatmapTabVisible()
    }

    override fun apply() {
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
        settingsService.setChartTabVisible(chartTabCheckBox.isSelected)
        settingsService.setHeatmapTabVisible(heatmapTabCheckBox.isSelected)

        Locale.setDefault(localizationService.currentLocale())

        ApplicationManager.getApplication().messageBus
            .syncPublisher(SettingsUpdateListener.TOPIC)
            .onSettingsUpdated()
    }

    override fun reset() {
        refreshModeCombo.selectedItem = settingsService.getRefreshMode()
        fixedIntervalCombo.selectedItem = settingsService.getFixedIntervalSec()
        openIntervalCombo.selectedItem = settingsService.getOpenIntervalSec()
        closedIntervalCombo.selectedItem = settingsService.getClosedIntervalSec()
        languageCombo.selectedItem = settingsService.getUiLanguage()
        marketPulseCheckBox.isSelected = settingsService.isMarketPulseVisible()
        chartTabCheckBox.isSelected = settingsService.isChartTabVisible()
        heatmapTabCheckBox.isSelected = settingsService.isHeatmapTabVisible()
        updateIntervalControlAvailability()
    }

    override fun disposeUIResources() {
        component = null
    }

    private fun updateIntervalControlAvailability() {
        val mode = refreshModeCombo.selectedItem as? AppSettingsService.RefreshMode ?: AppSettingsService.RefreshMode.AUTO
        fixedIntervalCombo.isEnabled = mode == AppSettingsService.RefreshMode.FIXED
        openIntervalCombo.isEnabled = mode == AppSettingsService.RefreshMode.AUTO
        closedIntervalCombo.isEnabled = mode == AppSettingsService.RefreshMode.AUTO
    }
}
