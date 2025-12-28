package com.github.myeoungdev.marketticker.ui.alert

import com.github.myeoungdev.marketticker.application.service.PriceAlertService
import com.github.myeoungdev.marketticker.domain.model.AlertRule
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.*
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.swing.JComponent

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-23
 */
private val logger = KotlinLogging.logger {}

class AlertSettingsDialog(
    private val ticker: Ticker
) : DialogWrapper(true) {

    private val priceAlertService = service<PriceAlertService>()

    private data class AlertViewModel(
        var useTargetPrice: Boolean = false,
        var targetPriceStr: String = "",
        var volatilityStr: String = "5",
        var useVolatility: Boolean = true,
    )

    private val model = AlertViewModel()

    init {
        title = "${ticker.name} 알람 설정"
        initSetting()
        init()
    }

    private fun initSetting() {
        val alert = priceAlertService.getAlert(ticker.symbol)

        logger.info { "Saved Alert ${alert}" }

        if (alert == null) {
            return
        }

        model.targetPriceStr = alert.targetPrice?.toString() ?: ""
        model.useTargetPrice = alert.isTargetPriceEnabled
        model.volatilityStr = alert.volatilityPercentage.toString()
        model.useVolatility = alert.isVolatilityEnabled

    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label("알림 조건을 설정하면 우측 하단에 알림이 표시됩니다.")
                    .comment("목표가 도달 시 또는 급격한 변동 발생 시 알림")
            }

            separator()

            row("종목명:") {
                label(ticker.name).bold()
            }

            row {
                val targetPriceCheckBoxValue = checkBox("목표가 알림")
                    .bindSelected(model::useTargetPrice)
                    .component

                cell()

                textField()
                    .bindText(model::targetPriceStr)
                    .columns(10)
                    .validationOnInput {
                        if (it.text.toDoubleOrNull() == null && it.text.isNotEmpty()) {
                            return@validationOnInput error("올바른 숫자를 입력하세요")
                        }
                        null
                    }
                    .onChanged {
                        targetPriceCheckBoxValue.isSelected = it.text.isNotEmpty()
                    }
            }

            separator()

            row {
                val volatilityCheckboxValue = checkBox("변동률 알림")
                    .bindSelected(model::useVolatility)
                    .component

                cell()

                textField()
                    .bindText(model::volatilityStr)
                    .columns(10)
                    .validationOnInput {
                        if (it.text.toDoubleOrNull() == null && it.text.isNotEmpty()) {
                            return@validationOnInput error("올바른 숫자를 입력하세요")
                        }
                        null
                    }
                    .onChanged {
                        volatilityCheckboxValue.isSelected = it.text.isNotEmpty()
                    }

                label("%").gap(RightGap.SMALL)
            }

            row {
                button("알람 삭제") {
                    deleteAlert()
                    close(OK_EXIT_CODE)
                }
            }.enabled(isExistingAlert())

        }.apply {
            withPreferredWidth(400)
        }
    }

    override fun doOKAction() {
        super.doOKAction()

        logger.info { "targetPriceStr ${model.targetPriceStr}" }
        logger.info { "volatilityStr ${model.volatilityStr}" }

        val targetPrice = model.targetPriceStr.toDoubleOrNull()
        val volatility = model.volatilityStr.toDoubleOrNull() ?: 5.0

        logger.info { "Saving Alert: symbol=${ticker.symbol}, targetPrice=$targetPrice volatility=$volatility" }

        val rule = AlertRule(
            symbol = ticker.symbol,
            tradingSymbol = ticker.tradingSymbol.ifBlank { ticker.symbol },
            targetPrice = targetPrice,
            volatilityPercentage = volatility,
            isEnabled = true
        )

        priceAlertService.addAlert(rule)
    }

    private fun deleteAlert() {
        priceAlertService.removeAlert(ticker.symbol)
    }

    private fun isExistingAlert(): Boolean {
        return priceAlertService.getAlert(ticker.symbol) != null
    }

}