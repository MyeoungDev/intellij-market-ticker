package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.listener.SettingsUpdateListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val pollingLoop = TickerPollingLoop(
        cs = cs,
        pollingEnabled = appSettingsService::isAutomaticPollingEnabled,
        refreshMode = appSettingsService::getRefreshMode,
        refreshPrices = { marketDataService.refreshPrices(PriceRefreshSource.AUTOMATIC) },
        fixedIntervalSec = appSettingsService::getFixedIntervalSec,
        openIntervalSec = appSettingsService::getOpenIntervalSec,
        closedIntervalSec = appSettingsService::getClosedIntervalSec
    )

    init {
        logger.info { "Scheduler Started." }
        if (appSettingsService.isAutomaticPollingEnabled()) {
            marketDataService.refreshPricesAsync(PriceRefreshSource.STARTUP)
        }
        subscribeSettingsUpdates()
        startPolling()
    }

    private fun startPolling() {
        pollingLoop.restart()
    }

    private fun subscribeSettingsUpdates() {
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(SettingsUpdateListener.TOPIC, object : SettingsUpdateListener {
                override fun onSettingsUpdated() {
                    logger.info { "Settings updated. Restart polling loop." }
                    startPolling()
                }
            })
    }
}

internal class TickerPollingLoop(
    private val cs: CoroutineScope,
    private val pollingEnabled: () -> Boolean,
    private val refreshMode: () -> AppSettingsService.RefreshMode,
    private val refreshPrices: suspend () -> PriceRefreshResult,
    private val fixedIntervalSec: () -> Long,
    private val openIntervalSec: () -> Long,
    private val closedIntervalSec: () -> Long,
    private val toDelayMillis: (Long) -> Long = { it * 1000L },
    private val errorDelayMillis: Long = 3_000L
) {
    private var pollingJob: Job? = null

    fun restart() {
        pollingJob?.cancel()
        pollingJob = cs.launch {
            while (isActive) {
                try {
                    if (!pollingEnabled()) {
                        delay(500L)
                        continue
                    }
                    when (refreshMode()) {
                        AppSettingsService.RefreshMode.MANUAL -> delay(500L)
                        AppSettingsService.RefreshMode.FIXED -> {
                            refreshPrices()
                            delay(toDelayMillis(fixedIntervalSec()))
                        }

                        AppSettingsService.RefreshMode.AUTO -> {
                            val result = refreshPrices()
                            val intervalSec = if (result.requested) {
                                openIntervalSec()
                            } else {
                                closedIntervalSec()
                            }
                            delay(toDelayMillis(intervalSec))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Error during polling loop" }
                    delay(errorDelayMillis)
                }
            }
        }
    }

    fun cancel() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
