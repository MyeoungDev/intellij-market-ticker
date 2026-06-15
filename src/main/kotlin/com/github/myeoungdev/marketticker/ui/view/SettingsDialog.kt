package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Component
import java.awt.Dimension
import java.util.Locale
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

/**
 * 사용자 설정을 메인 화면과 분리해 관리하는 모달 다이얼로그입니다.
 */
class SettingsDialog(
    private val onSaved: (() -> Unit)? = null
) : DialogWrapper(true) {

    private val settingsService = service<AppSettingsService>()
    private val localizationService = service<LocalizationService>()
    private val marketDataService = service<MarketDataService>()

    private val automaticPollingCheckBox = JCheckBox()
    private val refreshModeCombo = JComboBox(AppSettingsService.RefreshMode.values())
    private val fixedIntervalCombo = JComboBox(AppSettingsService.ACTIVE_INTERVAL_OPTIONS)
    private val openIntervalCombo = JComboBox(AppSettingsService.ACTIVE_INTERVAL_OPTIONS)
    private val closedIntervalCombo = JComboBox(AppSettingsService.CLOSED_INTERVAL_OPTIONS)
    private val languageCombo = JComboBox(AppSettingsService.UiLanguage.values())
    private val priceDisplayModeCombo = JComboBox(AppSettingsService.PriceDisplayMode.values())
    private val baseCurrencyCombo = JComboBox(arrayOf(
        CurrencyType.KRW,
        CurrencyType.USD,
        CurrencyType.HKD,
        CurrencyType.JPY,
        CurrencyType.CNY,
        CurrencyType.EUR
    ))
    private val marketPulseCheckBox = JCheckBox()
    private val marketSessionIndicatorCheckBox = JCheckBox()
    private val portfolioSummaryCheckBox = JCheckBox()

    init {
        title = localizationService.text("Market Ticker 설정", "Market Ticker Settings")

        fixedIntervalCombo.renderer = pollingIntervalRenderer()
        openIntervalCombo.renderer = pollingIntervalRenderer()
        closedIntervalCombo.renderer = pollingIntervalRenderer()

        automaticPollingCheckBox.isSelected = settingsService.isAutomaticPollingEnabled()
        refreshModeCombo.selectedItem = settingsService.getRefreshMode()
        fixedIntervalCombo.selectedItem = settingsService.getFixedIntervalSec()
        openIntervalCombo.selectedItem = settingsService.getOpenIntervalSec()
        closedIntervalCombo.selectedItem = settingsService.getClosedIntervalSec()
        languageCombo.selectedItem = settingsService.getUiLanguage()
        priceDisplayModeCombo.selectedItem = settingsService.getPriceDisplayMode()
        baseCurrencyCombo.selectedItem = settingsService.getBaseCurrency()
        marketPulseCheckBox.isSelected = settingsService.isMarketPulseVisible()
        marketSessionIndicatorCheckBox.isSelected = settingsService.isMarketSessionIndicatorVisible()
        portfolioSummaryCheckBox.isSelected = settingsService.isPortfolioSummaryVisible()

        automaticPollingCheckBox.addActionListener {
            updateIntervalControlAvailability()
            persistSettings()
        }
        refreshModeCombo.addActionListener {
            updateIntervalControlAvailability()
            persistSettings()
        }
        fixedIntervalCombo.addActionListener { persistSettings() }
        openIntervalCombo.addActionListener { persistSettings() }
        closedIntervalCombo.addActionListener { persistSettings() }
        languageCombo.addActionListener { persistSettings() }
        priceDisplayModeCombo.addActionListener { persistSettings() }
        baseCurrencyCombo.addActionListener { persistSettings() }
        marketPulseCheckBox.addActionListener { persistSettings() }
        marketSessionIndicatorCheckBox.addActionListener { persistSettings() }
        portfolioSummaryCheckBox.addActionListener { persistSettings() }

        updateIntervalControlAvailability()
        init()
        setSize(560, 420)
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                automaticPollingCheckBox.text = localizationService.text("자동 폴링 사용", "Enable automatic polling")
                cell(automaticPollingCheckBox)
            }

            row(localizationService.text("업데이트 모드", "Refresh mode")) {
                cell(refreshModeCombo).align(AlignX.FILL)
            }

            row(localizationService.text("고정 주기", "Fixed interval")) {
                cell(fixedIntervalCombo).align(AlignX.FILL)
            }

            row(localizationService.text("장중 주기", "Open market interval")) {
                cell(openIntervalCombo).align(AlignX.FILL)
            }

            row(localizationService.text("비장중 주기", "Closed market interval")) {
                cell(closedIntervalCombo).align(AlignX.FILL)
            }

            row(localizationService.text("언어", "Language")) {
                cell(languageCombo).align(AlignX.FILL)
            }

            row(localizationService.text("현재가 표기", "Price display")) {
                cell(priceDisplayModeCombo).align(AlignX.FILL)
            }

            row(localizationService.text("기준 통화", "Base currency")) {
                cell(baseCurrencyCombo).align(AlignX.FILL)
            }

            row {
                marketPulseCheckBox.text = localizationService.text("한 줄 지표 표시", "Show market pulse ticker")
                cell(marketPulseCheckBox)
            }

            row {
                marketSessionIndicatorCheckBox.text = localizationService.text("관심종목 시장 상태 표시", "Show watchlist market status")
                cell(marketSessionIndicatorCheckBox)
            }

            row {
                portfolioSummaryCheckBox.text = localizationService.text("포트폴리오 요약 표시", "Show portfolio summary")
                cell(portfolioSummaryCheckBox)
            }

            row {
                button(localizationService.text("지금 새로고침", "Refresh now")) {
                    marketDataService.forceRefresh()
                }
            }
        }.apply {
            preferredSize = Dimension(520, 330)
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
        val closed = closedIntervalCombo.selectedItem as? Long ?: AppSettingsService.DEFAULT_CLOSED_INTERVAL_SEC
        val language = languageCombo.selectedItem as? AppSettingsService.UiLanguage ?: AppSettingsService.UiLanguage.AUTO
        val priceDisplayMode = priceDisplayModeCombo.selectedItem as? AppSettingsService.PriceDisplayMode
            ?: AppSettingsService.PriceDisplayMode.MIXED
        val baseCurrency = baseCurrencyCombo.selectedItem as? CurrencyType ?: CurrencyType.KRW

        settingsService.setAutomaticPollingEnabled(automaticPollingCheckBox.isSelected)
        settingsService.setRefreshMode(mode)
        settingsService.setFixedIntervalSec(fixed)
        settingsService.setOpenIntervalSec(open)
        settingsService.setClosedIntervalSec(closed)
        settingsService.setUiLanguage(language)
        settingsService.setPriceDisplayMode(priceDisplayMode)
        settingsService.setBaseCurrency(baseCurrency)
        settingsService.setMarketPulseVisible(marketPulseCheckBox.isSelected)
        settingsService.setMarketSessionIndicatorVisible(marketSessionIndicatorCheckBox.isSelected)
        settingsService.setPortfolioSummaryVisible(portfolioSummaryCheckBox.isSelected)

        Locale.setDefault(localizationService.currentLocale())
        ApplicationManager.getApplication().messageBus
            .syncPublisher(SettingsUpdateListener.TOPIC)
            .onSettingsUpdated()
    }

    private fun updateIntervalControlAvailability() {
        val automaticPollingEnabled = automaticPollingCheckBox.isSelected
        val mode = refreshModeCombo.selectedItem as? AppSettingsService.RefreshMode ?: AppSettingsService.RefreshMode.AUTO
        refreshModeCombo.isEnabled = automaticPollingEnabled
        fixedIntervalCombo.isEnabled = automaticPollingEnabled && mode == AppSettingsService.RefreshMode.FIXED
        openIntervalCombo.isEnabled = automaticPollingEnabled && mode == AppSettingsService.RefreshMode.AUTO
        closedIntervalCombo.isEnabled = automaticPollingEnabled && mode == AppSettingsService.RefreshMode.AUTO
    }

    private fun pollingIntervalRenderer(): DefaultListCellRenderer {
        return object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus
                ) as JLabel
                label.text = AppSettingsService.formatPollingInterval(value as? Long ?: 0L)
                return label
            }
        }
    }
}
