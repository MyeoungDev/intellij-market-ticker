package com.github.myeoungdev.marketticker.ui.alert

import com.github.myeoungdev.marketticker.application.service.PriceAlertService
import com.github.myeoungdev.marketticker.domain.model.AlertRule
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.swing.JButton
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
        var targetPrice: Double = 0.0,
        var volatility: Double = 5.0
    )

    private val model = AlertViewModel()

    private val targetPriceField = JBTextField()
    private val volatilityField = JBTextField("5.0")
    private val saveButton = JButton("알람 저장")

    init {
        title = "${ticker.name} 알람 설정"
        loadExistingSettings()
        init()
    }

    private fun loadExistingSettings() {
        val existing = priceAlertService.getAlert(ticker.tradingSymbol)
        if (existing != null) {
            model.targetPrice = existing.targetPrice ?: 0.0
            model.volatility = existing.volatilityPercentage
        }
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label("알림 조건을 설정하면 우측 하단에 알림이 표시됩니다.")
                    .comment("목표가 도달 시 또는 급격한 변동 발생 시 알림")
            }

            separator()

            row("종목명:") {
                label(ticker.name)
            }
            row("목표가:") {
                textField()
                    .bindText(
                        { model.targetPrice?.toString() ?: "" },
                        { model.volatility = it.toDoubleOrNull() ?: 0.0 }
                    )
                    .columns(10)
                    .validationOnInput {
                        if (it.text.toDoubleOrNull() == null && it.text.isNotEmpty()) {
                            return@validationOnInput error("올바른 숫자를 입력하세요")
                        }
                        null
                    }
            }


            row("변동률 알람(±%):") {
                textField()
                    .bindText(
                        { model.targetPrice?.toString() ?: "" },
                        { model.volatility = it.toDoubleOrNull() ?: 0.0 }
                    )
                    .columns(10)
                    .validationOnInput {
                        if (it.text.toDoubleOrNull() == null && it.text.isNotEmpty()) {
                            return@validationOnInput error("올바른 숫자를 입력하세요")
                        }
                        null
                    }
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

        val targetPrice = model.targetPrice
        val volatility = model.volatility ?: 5.0

        logger.info { "Saving Alert: symbol=${ticker.symbol}, targetPrice=$targetPrice vol=$volatility" }
        val rule = AlertRule(
            symbol = ticker.symbol,
            tradingSymbol = ticker.tradingSymbol.ifBlank { ticker.symbol },
            targetPrice = model.targetPrice,
            volatilityPercentage = volatility,
            isEnabled = true
        )

        priceAlertService.addAlert(rule)
    }

    private fun deleteAlert() {
        val key = ticker.tradingSymbol.ifBlank { ticker.symbol }
        priceAlertService.removeAlert(key)
    }

    private fun isExistingAlert(): Boolean {
        val key = ticker.tradingSymbol.ifBlank { ticker.symbol }
        return priceAlertService.getAlert(key) != null
    }

}