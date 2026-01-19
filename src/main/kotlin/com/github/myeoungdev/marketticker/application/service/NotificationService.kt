package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Notification 기능을 담당하는 서비스 클래스 입니다.
 *
 * @author : 강명관
 * @since : 2026-01-18
 **/
@Service(Service.Level.APP)
class NotificationService {

    private val priceAlertService = service<PriceAlertService>()

    private val lastAlertTimeMap = ConcurrentHashMap<String, Long>()
    private val ALERT_COOLDOWN_FIVE_MINUTE = 5 * 60 * 1000L

    /**
     * Ticker 의 현재 가격을 통해 등록된 알람 기준에 맞는지 판별하고
     * 알람 기준에 충족할 시 알람을 발송하는 메서드 입니다.
     *
     * @param prices TickerPrices
     */
    fun checkAndNotify(prices: List<TickerPrice>) {
        prices.forEach { price ->
            // 1. 규칙 위반 여부 확인 (PriceAlertService 위임)
            if (priceAlertService.shouldTriggerAlert(price)) {
                sendNotification(price)
            }
        }
    }

    /**
     * 알림 형식을 생성하여 활성화된 프로젝트에 알림을 보내는 메서드 입니ㅏㄷ.
     *
     * @param tickerPrice TickerPrice
     */
    private fun sendNotification(tickerPrice: TickerPrice) {
        // NOTE: 알림 쿨다운 체크
        val now = System.currentTimeMillis()
        val lastTime = lastAlertTimeMap.getOrDefault(tickerPrice.symbol, 0L)
        if (now - lastTime < ALERT_COOLDOWN_FIVE_MINUTE) return

        lastAlertTimeMap[tickerPrice.symbol] = now

        // NOTE: 알림 생성
        val group = NotificationGroupManager.getInstance()
            .getNotificationGroup("Market Ticker Notification")

        if (group == null) {
            logger.error { "Notification group not found!" }
            return
        }

        val isRising = tickerPrice.changeRate > 0
        val colorHex = if (isRising) "#FF5252" else "#448AFF"
        val arrow = if (isRising) "▲" else "▼"

        val title = "${tickerPrice.name} $arrow ${tickerPrice.changeRate}%"
        val sign = if (tickerPrice.changeAmount > 0) "+" else ""
        val changeText = "$sign${tickerPrice.changeAmount} ${tickerPrice.currency.symbol}"

        val content = """
        <html>
        <body>
            <div style="margin-top: 4px;">
                <b>${tickerPrice.currentPrice}</b>
                <span style="color:$colorHex;">($changeText)</span>
            </div>
        </body>
        </html>
        """.trimIndent()

        val notification = group.createNotification(
            title,
            content,
            NotificationType.INFORMATION
        )

        notification.addAction(
            NotificationAction.createSimple("상세 보기") {
                val url = "https://finance.naver.com/item/main.nhn?code=${tickerPrice.symbol}"
                BrowserUtil.browse(url)
            }
        )

        // NOTE: 활성화된 Project 에 알림 발송
        val targetProject = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
        notification.notify(targetProject)
    }
}