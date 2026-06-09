package com.github.myeoungdev.marketticker.ui.settings

import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.util.Locale
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

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
    private val priceDisplayModeCombo = JComboBox(AppSettingsService.PriceDisplayMode.values())
    private val domesticTradeVenueModeCombo = JComboBox(AppSettingsService.DomesticTradeVenueMode.values())
    private val domesticTradeVenueDescriptionLabel = JLabel()
    private val baseCurrencyCombo = JComboBox(arrayOf(
        CurrencyType.KRW,
        CurrencyType.USD,
        CurrencyType.HKD,
        CurrencyType.JPY,
        CurrencyType.CNY,
        CurrencyType.EUR
    ))
    private val marketPulseCheckBox = JCheckBox()
    private val chartTabCheckBox = JCheckBox()
    private val heatmapTabCheckBox = JCheckBox()

    private var component: JComponent? = null

    override fun getDisplayName(): String = "Market Ticker"

    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent {
        if (component == null) {
            refreshModeCombo.addActionListener { updateIntervalControlAvailability() }
            domesticTradeVenueModeCombo.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                    label.text = displayDomesticTradeVenueMode(
                        value as? AppSettingsService.DomesticTradeVenueMode
                            ?: AppSettingsService.DomesticTradeVenueMode.MIXED
                    )
                    return label
                }
            }
            domesticTradeVenueDescriptionLabel.text = localizationService.text(
                "KRX + NXT 혼합은 정규장(09:00~15:30)에는 KRX, NXT 거래 가능 시간(08:00~20:00) 중 정규장 외 시간에는 NXT 기준으로 표시합니다.",
                "KRX + NXT Mixed uses KRX during regular market hours (09:00-15:30), and NXT outside regular hours when NXT is available (08:00-20:00)."
            )
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

                row(localizationService.text("현재가 표기", "Price display")) {
                    cell(priceDisplayModeCombo).align(AlignX.FILL)
                }

                row(localizationService.text("국내 주식 시세 기준", "Domestic stock venue")) {
                    cell(domesticTradeVenueModeCombo).align(AlignX.FILL)
                }

                row {
                    cell(domesticTradeVenueDescriptionLabel)
                }

                row(localizationService.text("기준 통화", "Base currency")) {
                    cell(baseCurrencyCombo).align(AlignX.FILL)
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
        val priceDisplayMode = priceDisplayModeCombo.selectedItem as? AppSettingsService.PriceDisplayMode
            ?: AppSettingsService.PriceDisplayMode.MIXED
        val domesticTradeVenueMode = domesticTradeVenueModeCombo.selectedItem as? AppSettingsService.DomesticTradeVenueMode
            ?: AppSettingsService.DomesticTradeVenueMode.MIXED
        val baseCurrency = baseCurrencyCombo.selectedItem as? CurrencyType ?: CurrencyType.KRW

        return mode != settingsService.getRefreshMode() ||
                fixed != settingsService.getFixedIntervalSec() ||
                open != settingsService.getOpenIntervalSec() ||
                closed != settingsService.getClosedIntervalSec() ||
                language != settingsService.getUiLanguage() ||
                priceDisplayMode != settingsService.getPriceDisplayMode() ||
                domesticTradeVenueMode != settingsService.getDomesticTradeVenueMode() ||
                baseCurrency != settingsService.getBaseCurrency() ||
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
        val priceDisplayMode = priceDisplayModeCombo.selectedItem as? AppSettingsService.PriceDisplayMode
            ?: AppSettingsService.PriceDisplayMode.MIXED
        val domesticTradeVenueMode = domesticTradeVenueModeCombo.selectedItem as? AppSettingsService.DomesticTradeVenueMode
            ?: AppSettingsService.DomesticTradeVenueMode.MIXED
        val baseCurrency = baseCurrencyCombo.selectedItem as? CurrencyType ?: CurrencyType.KRW

        settingsService.setRefreshMode(mode)
        settingsService.setFixedIntervalSec(fixed)
        settingsService.setOpenIntervalSec(open)
        settingsService.setClosedIntervalSec(closed)
        settingsService.setUiLanguage(language)
        settingsService.setPriceDisplayMode(priceDisplayMode)
        settingsService.setDomesticTradeVenueMode(domesticTradeVenueMode)
        settingsService.setBaseCurrency(baseCurrency)
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
        priceDisplayModeCombo.selectedItem = settingsService.getPriceDisplayMode()
        domesticTradeVenueModeCombo.selectedItem = settingsService.getDomesticTradeVenueMode()
        baseCurrencyCombo.selectedItem = settingsService.getBaseCurrency()
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

    private fun displayDomesticTradeVenueMode(mode: AppSettingsService.DomesticTradeVenueMode): String {
        return when (mode) {
            AppSettingsService.DomesticTradeVenueMode.KRX_ONLY -> localizationService.text("KRX 고정", "KRX only")
            AppSettingsService.DomesticTradeVenueMode.NXT_ONLY -> localizationService.text("NXT 고정", "NXT only")
            AppSettingsService.DomesticTradeVenueMode.MIXED -> localizationService.text("KRX + NXT 혼합", "KRX + NXT mixed")
        }
    }
}
