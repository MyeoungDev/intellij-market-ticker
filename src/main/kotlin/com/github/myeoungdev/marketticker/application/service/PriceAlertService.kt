package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.AlertMode
import com.github.myeoungdev.marketticker.domain.model.AlertRule
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

/**
 * Ticker 에 대한 알람을 관리하는 클래스 입니다.
 *
 * @author  : 강명관
 * @since   : 2025-12-23
 */
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

    /**
     * Ticker 의 알람 규칙을 저장하는 메서드 입니다.
     *
     * @param rule Ticker 의 사용자 지정 알람 규칙
     */
    fun addAlert(rule: AlertRule) {
        val key = normalize(rule.symbol)
        val existing = alertState.alerts[key]
        alertState.alerts[key] = rule.copy(
            symbol = key,
            triggeredOnce = existing?.triggeredOnce ?: false
        )
    }

    /**
     * 저장되어 있는 알람 규칙을 가져오는 메서드 입니다.
     *
     * @param symbol Ticker 구분자
     * @return AlertRule
     */
    fun getAlert(symbol: String): AlertRule? {
        return alertState.alerts[normalize(symbol)]
    }

    /**
     * 알람 규칙을 삭제하는 메서드 입니다.
     *
     * @param symbol Ticker 구분자
     */
    fun removeAlert(symbol: String) {
        alertState.alerts.remove(normalize(symbol))
    }

    /**
     * Ticker 의 현재 가격에 대해 저장되어 있는 알람 규칙을 확인하여 알람을 보낼지에 대해 판단하는 메서드 입니다.
     *
     * @param price Ticker 의 현재 가격
     * @return
     */
    fun shouldTriggerAlert(price: TickerPrice): Boolean {
        val rule = getAlert(price.symbol) ?: return false

        logger.debug { "Checking alert: rate=${price.changeRate}, rule=${rule.volatilityPercentage}" }

        if (!rule.isEnabled) {
            return false
        }

        if (rule.marketHoursOnly && price.marketStatus != MarketStatus.OPEN) {
            return false
        }

        if (AlertMode.of(rule.alertMode) == AlertMode.ONCE && rule.triggeredOnce) {
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

    /**
     * 1회성 알림이 발송된 종목을 트리거 완료 상태로 표시합니다.
     */
    fun markTriggered(symbol: String) {
        val key = normalize(symbol)
        val rule = alertState.alerts[key] ?: return
        rule.triggeredOnce = true
    }

    private fun normalize(symbol: String): String = symbol.uppercase()

}
