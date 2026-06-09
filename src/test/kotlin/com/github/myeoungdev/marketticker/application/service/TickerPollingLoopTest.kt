package com.github.myeoungdev.marketticker.application.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class TickerPollingLoopTest {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `고정 주기 변경은 기존 긴 대기를 취소하고 새 주기를 즉시 반영한다`() {
        val fixedIntervalSec = AtomicLong(900L)
        val firstRefresh = CountDownLatch(1)
        val secondRefresh = CountDownLatch(1)
        val refreshCount = AtomicInteger()
        val loop = tickerPollingLoop(
            refreshMode = { AppSettingsService.RefreshMode.FIXED },
            fixedIntervalSec = fixedIntervalSec::get,
            refreshPrices = {
                when (refreshCount.incrementAndGet()) {
                    1 -> firstRefresh.countDown()
                    2 -> secondRefresh.countDown()
                }
            }
        )

        loop.restart()
        assertThat(firstRefresh.await(1, TimeUnit.SECONDS)).isTrue()

        fixedIntervalSec.set(3L)
        loop.restart()

        assertThat(secondRefresh.await(500, TimeUnit.MILLISECONDS)).isTrue()
        loop.cancel()
    }

    @Test
    fun `장중 자동 주기 변경은 기존 긴 대기를 취소하고 새 주기를 즉시 반영한다`() {
        val openIntervalSec = AtomicLong(900L)
        val firstRefresh = CountDownLatch(1)
        val secondRefresh = CountDownLatch(1)
        val refreshCount = AtomicInteger()
        val loop = tickerPollingLoop(
            refreshMode = { AppSettingsService.RefreshMode.AUTO },
            hasOpenMarket = { true },
            openIntervalSec = openIntervalSec::get,
            refreshPrices = {
                when (refreshCount.incrementAndGet()) {
                    1 -> firstRefresh.countDown()
                    2 -> secondRefresh.countDown()
                }
            }
        )

        loop.restart()
        assertThat(firstRefresh.await(1, TimeUnit.SECONDS)).isTrue()

        openIntervalSec.set(3L)
        loop.restart()

        assertThat(secondRefresh.await(500, TimeUnit.MILLISECONDS)).isTrue()
        loop.cancel()
    }

    @Test
    fun `모드 변경은 이전 모드의 긴 대기를 취소하고 변경된 모드로 즉시 재평가한다`() {
        val refreshMode = AtomicReference(AppSettingsService.RefreshMode.FIXED)
        val fixedIntervalSec = AtomicLong(900L)
        val firstRefresh = CountDownLatch(1)
        val secondRefresh = CountDownLatch(1)
        val refreshCount = AtomicInteger()
        val loop = tickerPollingLoop(
            refreshMode = refreshMode::get,
            fixedIntervalSec = fixedIntervalSec::get,
            openIntervalSec = { 3L },
            refreshPrices = {
                when (refreshCount.incrementAndGet()) {
                    1 -> firstRefresh.countDown()
                    2 -> secondRefresh.countDown()
                }
            }
        )

        loop.restart()
        assertThat(firstRefresh.await(1, TimeUnit.SECONDS)).isTrue()

        refreshMode.set(AppSettingsService.RefreshMode.AUTO)
        loop.restart()

        assertThat(secondRefresh.await(500, TimeUnit.MILLISECONDS)).isTrue()
        loop.cancel()
    }

    private fun tickerPollingLoop(
        refreshMode: () -> AppSettingsService.RefreshMode,
        refreshPrices: suspend () -> Unit,
        hasOpenMarket: () -> Boolean = { true },
        fixedIntervalSec: () -> Long = { 3L },
        openIntervalSec: () -> Long = { 3L },
        closedIntervalSec: () -> Long = { 10L }
    ): TickerPollingLoop {
        return TickerPollingLoop(
            cs = scope,
            refreshMode = refreshMode,
            refreshPrices = refreshPrices,
            hasOpenMarket = hasOpenMarket,
            fixedIntervalSec = fixedIntervalSec,
            openIntervalSec = openIntervalSec,
            closedIntervalSec = closedIntervalSec,
            toDelayMillis = { it * 10L },
            errorDelayMillis = 10L
        )
    }
}
