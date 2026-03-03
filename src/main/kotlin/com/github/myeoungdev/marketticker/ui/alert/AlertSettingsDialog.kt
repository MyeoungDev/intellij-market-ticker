package com.github.myeoungdev.marketticker.ui.alert

import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.application.service.PriceAlertService
import com.github.myeoungdev.marketticker.domain.model.AlertMode
import com.github.myeoungdev.marketticker.domain.model.AlertRule
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.swing.JComboBox
import javax.swing.JComponent

private val logger = KotlinLogging.logger {}

class AlertSettingsDialog(
    private val ticker: Ticker
) : DialogWrapper(true) {

    private val priceAlertService = service<PriceAlertService>()
    private val localizationService = service<LocalizationService>()

    private data class AlertViewModel(
        var useTargetPrice: Boolean = false,
        var targetPriceStr: String = "",
        var volatilityStr: String = "5",
        var useVolatility: Boolean = true,
        var marketHoursOnly: Boolean = false,
        var soundEnabled: Boolean = false,
        var repeatIntervalMinutesStr: String = "5",
        var alertMode: AlertMode = AlertMode.REPEATING,
    )

    private val model = AlertViewModel()
    private val modeCombo = JComboBox(AlertMode.values())

    init {
        title = localizationService.text("${ticker.name} 알림 설정", "${ticker.name} Alert Settings")
        initSetting()
        modeCombo.selectedItem = model.alertMode
        init()
    }

    private fun initSetting() {
        val alert = priceAlertService.getAlert(ticker.symbol) ?: return

        model.targetPriceStr = alert.targetPrice?.toString() ?: ""
        model.useTargetPrice = alert.isTargetPriceEnabled
        model.volatilityStr = alert.volatilityPercentage.toString()
        model.useVolatility = alert.isVolatilityEnabled
        model.marketHoursOnly = alert.marketHoursOnly
        model.soundEnabled = alert.soundEnabled
        model.repeatIntervalMinutesStr = alert.repeatIntervalMinutes.toString()
        model.alertMode = AlertMode.of(alert.alertMode)
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                text(localizationService.text("조건 충족 시 우측 하단 알림을 표시합니다.", "When conditions match, notifications appear at the bottom-right."))
            }

            row(localizationService.text("종목", "Symbol")) {
                text(ticker.name)
            }

            row {
                val targetPriceCheckBox = checkBox(localizationService.text("목표가", "Target price"))
                    .bindSelected(model::useTargetPrice)
                    .component

                cell()

                textField()
                    .bindText(model::targetPriceStr)
                    .columns(10)
                    .onChanged {
                        targetPriceCheckBox.isSelected = it.text.isNotEmpty()
                    }
            }

            row {
                val volatilityCheckBox = checkBox(localizationService.text("변동률", "Volatility"))
                    .bindSelected(model::useVolatility)
                    .component

                cell()

                textField()
                    .bindText(model::volatilityStr)
                    .columns(10)
                    .onChanged {
                        volatilityCheckBox.isSelected = it.text.isNotEmpty()
                    }

                text("%")
            }

            row(localizationService.text("알림 방식", "Alert mode")) {
                cell(modeCombo).align(AlignX.FILL)
            }

            row(localizationService.text("반복 간격(분)", "Repeat interval (min)")) {
                textField()
                    .bindText(model::repeatIntervalMinutesStr)
                    .columns(6)
            }

            row {
                checkBox(localizationService.text("장중에만 알림", "Only during market hours"))
                    .bindSelected(model::marketHoursOnly)
            }

            row {
                checkBox(localizationService.text("알림 소리", "Sound on alert"))
                    .bindSelected(model::soundEnabled)
            }

            row {
                button(localizationService.text("알림 삭제", "Delete alert")) {
                    priceAlertService.removeAlert(ticker.symbol)
                    close(OK_EXIT_CODE)
                }
            }.enabled(isExistingAlert())
        }
    }

    override fun doOKAction() {
        val targetPrice = model.targetPriceStr.toDoubleOrNull()
        val volatility = model.volatilityStr.toDoubleOrNull() ?: 5.0
        val repeatInterval = model.repeatIntervalMinutesStr.toIntOrNull()?.coerceIn(1, 240) ?: 5
        val selectedMode = modeCombo.selectedItem as? AlertMode ?: AlertMode.REPEATING

        val rule = AlertRule(
            symbol = ticker.symbol,
            tradingSymbol = ticker.tradingSymbol.ifBlank { ticker.symbol },
            targetPrice = targetPrice,
            isTargetPriceEnabled = model.useTargetPrice,
            volatilityPercentage = volatility,
            isVolatilityEnabled = model.useVolatility,
            isEnabled = true,
            alertMode = selectedMode.name,
            repeatIntervalMinutes = repeatInterval,
            marketHoursOnly = model.marketHoursOnly,
            soundEnabled = model.soundEnabled,
            triggeredOnce = false
        )

        logger.info { "Saving alert for ${ticker.symbol}: mode=$selectedMode, repeat=$repeatInterval" }
        priceAlertService.addAlert(rule)

        super.doOKAction()
    }

    private fun isExistingAlert(): Boolean {
        return priceAlertService.getAlert(ticker.symbol) != null
    }
}
