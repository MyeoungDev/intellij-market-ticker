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
    name = "PriceAlertService", // 이 이름이 XML 파일의 루트 태그가 됨
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


    // 메모리에 알람 규칙 저장 (앱 재시작 시 사라짐 -> 영속화 필요 시 PersistentStateComponent 구현)
    private val alerts = mutableMapOf<String, AlertRule>()

    // 알림 중복 방지용 (이미 알린 종목은 잠시 쿨타임 등을 줄 수 있음)
    // 여기서는 간단하게 구현
    private val notifiedTickers = mutableSetOf<String>()

    fun addAlert(rule: AlertRule) {
        alertState.alerts[rule.tradingSymbol] = rule
    }

    fun getAlert(symbol: String): AlertRule? {
        return alertState.alerts[symbol]
    }

    fun removeAlert(tradingSymbol: String) {
        alertState.alerts.remove(tradingSymbol)
    }

    /**
     * 알람 발생 여부를 판단하는 순수 함수
     * @param price 현재 가격 정보
     * @return 알람 발생 여부 (true/false)
     */
    fun shouldTriggerAlert(price: TickerPrice): Boolean {
        val rule = getAlert(price.tradingSymbol) ?: return false

        logger.info { "Checking alert: rate=${price.changeRate}, rule=${rule.volatilityPercentage}" }

        if (!rule.isEnabled) {
            return false
        }

        // 1. 목표가 체크 (설정된 경우만)
        if (rule.targetPrice != null) {
            // 현재가가 목표가에 도달했는지 체크 (오차범위 0.5% 내 진입 시)
            // 더 정교한 로직은 '이전 가격'이 필요하지만, 여기서는 근접 여부로 판단
            val gap = abs(price.currentPrice - rule.targetPrice!!)
            val threshold = rule.targetPrice!! * 0.005
            if (gap <= threshold) return true
        }

        // 2. 변동률 체크
        // 절대값 비교: abs(등락률) >= 설정한 변동률
        // 예: -3% 하락이고 설정이 2%면 알람 발생 (|-3| >= 2 -> true)
        if (abs(price.changeRate) >= rule.volatilityPercentage) {
            return true
        }

        return false
    }

}