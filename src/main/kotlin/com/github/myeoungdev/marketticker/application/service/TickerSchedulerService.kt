package com.github.myeoungdev.marketticker.application.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}


/**
 * Ticker 의 가격 리프레시 스케줄링 담당하는 서비스 클래스 입니다.
 *
 * @author : 강명관
 * @since : 2026-01-18
 */
@Service(Service.Level.APP)
class TickerSchedulerService(
    private val cs: CoroutineScope
) {
    private val marketDataService = service<MarketDataService>()

    companion object {
        const val POLLING_INTERVAL_MS = 6000L
    }

    init {
        logger.info { "Scheduler Started." }
        startPolling()
    }

    private fun startPolling() {
        cs.launch {
            while (isActive) {
                try {
                    marketDataService.refreshPrices()
                } catch (e: Exception) {
                    logger.error(e) { "Error during polling loop" }
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }
}