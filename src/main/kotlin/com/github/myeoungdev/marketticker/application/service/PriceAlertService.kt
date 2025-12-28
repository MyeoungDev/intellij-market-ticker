package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.AlertRule
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.abs

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-23
 */
private val logger = KotlinLogging.logger {}

@State(
    name = "PriceAlertService",
    storages = [Storage("market_ticker_alerts.xml")]
)
@Service(Service.Level.APP)
class PriceAlertService : PersistentStateComponent<PriceAlertService.State> {

    data class State(
        var alerts: MutableMap<String, AlertRule> = mutableMapOf()
    )

    private var alertState = State()

    override fun getState(): State = alertState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, alertState)
    }

    fun addAlert(rule: AlertRule) {
        alertState.alerts[rule.tradingSymbol] = rule
    }

    fun getAlert(symbol: String): AlertRule? {
        return alertState.alerts[symbol]
    }

    fun removeAlert(tradingSymbol: String) {
        alertState.alerts.remove(tradingSymbol)
    }

    fun shouldTriggerAlert(price: TickerPrice): Boolean {
        val rule = getAlert(price.symbol) ?: return false

        logger.info { "Checking alert: rate=${price.changeRate}, rule=${rule.volatilityPercentage}" }

        if (!rule.isEnabled) {
            return false
        }

        // NOTE: 목표가 알람 체크
        if (rule.isTargetPriceEnabled && rule.targetPrice != null) {
            val gap = abs(price.currentPrice - rule.targetPrice!!)
            val threshold = rule.targetPrice!! * 0.005
            if (gap <= threshold) {
                return true
            }
        }

        // NOTE: 변동률 알람 체크
        if (rule.isVolatilityEnabled && (abs(price.changeRate) >= rule.volatilityPercentage)) {
            return true
        }

        return false
    }

}