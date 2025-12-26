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
        val rule = getAlert(price.tradingSymbol) ?: return false

        logger.info { "Checking alert: rate=${price.changeRate}, rule=${rule.volatilityPercentage}" }

        if (!rule.isEnabled) {
            return false
        }

        // 1. 목표가 체크 (설정된 경우만)
        if (rule.targetPrice != null) {
            // 현재가가 목표가에 도달했는지 체크 (오차범위 0.5% 내 진입 시)
            val gap = abs(price.currentPrice - rule.targetPrice!!)
            val threshold = rule.targetPrice!! * 0.005
            if (gap <= threshold) return true
        }

        // 2. 변동률 체크
        // 절대값 비교: abs(등락률) >= 설정한 변동률
        if (abs(price.changeRate) >= rule.volatilityPercentage) {
            return true
        }

        return false
    }

}