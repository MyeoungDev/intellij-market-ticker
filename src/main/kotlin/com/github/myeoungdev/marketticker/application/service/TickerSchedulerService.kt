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
    private val appSettingsService = service<AppSettingsService>()

    init {
        logger.info { "Scheduler Started." }
        marketDataService.forceRefresh()
        startPolling()
    }

    private fun startPolling() {
        cs.launch {
            while (isActive) {
                try {
                    when (appSettingsService.getRefreshMode()) {
                        AppSettingsService.RefreshMode.MANUAL -> delay(500L)
                        AppSettingsService.RefreshMode.FIXED -> {
                            marketDataService.refreshPrices()
                            delay(appSettingsService.getFixedIntervalSec() * 1000L)
                        }

                        AppSettingsService.RefreshMode.AUTO -> {
                            marketDataService.refreshPrices()
                            val hasOpenMarket = marketDataService.currentPrices.value.any {
                                it.marketStatus.name == "OPEN" || it.marketStatus.name == "EXTENDED"
                            }
                            val intervalSec = if (hasOpenMarket) {
                                appSettingsService.getOpenIntervalSec()
                            } else {
                                appSettingsService.getClosedIntervalSec()
                            }
                            delay(intervalSec * 1000L)
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error during polling loop" }
                    delay(3_000L)
                }
            }
        }
    }
}
