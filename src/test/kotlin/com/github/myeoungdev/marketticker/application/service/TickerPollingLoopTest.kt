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
                PriceRefreshResult(requested = true, fetchedCount = 1)
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
            openIntervalSec = openIntervalSec::get,
            refreshPrices = {
                when (refreshCount.incrementAndGet()) {
                    1 -> firstRefresh.countDown()
                    2 -> secondRefresh.countDown()
                }
                PriceRefreshResult(requested = true, fetchedCount = 1)
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
    fun `자동 주기는 가격 요청이 생략되면 비장중 주기를 사용한다`() {
        val firstRefresh = CountDownLatch(1)
        val secondRefresh = CountDownLatch(1)
        val refreshCount = AtomicInteger()
        val loop = tickerPollingLoop(
            refreshMode = { AppSettingsService.RefreshMode.AUTO },
            closedIntervalSec = { 3L },
            refreshPrices = {
                when (refreshCount.incrementAndGet()) {
                    1 -> firstRefresh.countDown()
                    2 -> secondRefresh.countDown()
                }
                PriceRefreshResult(requested = false, fetchedCount = 0)
            }
        )

        loop.restart()

        assertThat(firstRefresh.await(1, TimeUnit.SECONDS)).isTrue()
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
                PriceRefreshResult(requested = true, fetchedCount = 1)
            }
        )

        loop.restart()
        assertThat(firstRefresh.await(1, TimeUnit.SECONDS)).isTrue()

        refreshMode.set(AppSettingsService.RefreshMode.AUTO)
        loop.restart()

        assertThat(secondRefresh.await(500, TimeUnit.MILLISECONDS)).isTrue()
        loop.cancel()
    }

    @Test
    fun `자동 폴링이 비활성화되면 가격 갱신을 호출하지 않는다`() {
        val refreshCount = AtomicInteger()
        val loop = tickerPollingLoop(
            pollingEnabled = { false },
            refreshMode = { AppSettingsService.RefreshMode.FIXED },
            refreshPrices = {
                refreshCount.incrementAndGet()
                PriceRefreshResult(requested = true, fetchedCount = 1)
            }
        )

        loop.restart()
        Thread.sleep(100)

        assertThat(refreshCount.get()).isZero()
        loop.cancel()
    }

    @Test
    fun `자동 폴링이 다시 활성화되면 가격 갱신을 재개한다`() {
        val pollingEnabled = AtomicReference(false)
        val firstRefresh = CountDownLatch(1)
        val loop = tickerPollingLoop(
            pollingEnabled = pollingEnabled::get,
            refreshMode = { AppSettingsService.RefreshMode.FIXED },
            refreshPrices = {
                firstRefresh.countDown()
                PriceRefreshResult(requested = true, fetchedCount = 1)
            }
        )

        loop.restart()
        assertThat(firstRefresh.await(100, TimeUnit.MILLISECONDS)).isFalse()

        pollingEnabled.set(true)

        assertThat(firstRefresh.await(1, TimeUnit.SECONDS)).isTrue()
        loop.cancel()
    }

    @Test
    fun `자동 폴링이 활성 상태에서 비활성화되면 이후 가격 갱신을 중단한다`() {
        val pollingEnabled = AtomicReference(true)
        val firstRefresh = CountDownLatch(1)
        val secondRefresh = CountDownLatch(1)
        val refreshCount = AtomicInteger()
        val loop = tickerPollingLoop(
            pollingEnabled = pollingEnabled::get,
            refreshMode = { AppSettingsService.RefreshMode.FIXED },
            fixedIntervalSec = { 3L },
            refreshPrices = {
                when (refreshCount.incrementAndGet()) {
                    1 -> firstRefresh.countDown()
                    2 -> secondRefresh.countDown()
                }
                PriceRefreshResult(requested = true, fetchedCount = 1)
            }
        )

        loop.restart()
        assertThat(firstRefresh.await(1, TimeUnit.SECONDS)).isTrue()

        pollingEnabled.set(false)
        loop.restart()

        assertThat(secondRefresh.await(150, TimeUnit.MILLISECONDS)).isFalse()
        assertThat(refreshCount.get()).isEqualTo(1)
        loop.cancel()
    }

    @Test
    fun `비활성 상태에서 변경한 고정 주기는 재활성화 후 다음 대기에 반영된다`() {
        val pollingEnabled = AtomicReference(false)
        val fixedIntervalSec = AtomicLong(900L)
        val firstRefresh = CountDownLatch(1)
        val secondRefresh = CountDownLatch(1)
        val refreshCount = AtomicInteger()
        val loop = tickerPollingLoop(
            pollingEnabled = pollingEnabled::get,
            refreshMode = { AppSettingsService.RefreshMode.FIXED },
            fixedIntervalSec = fixedIntervalSec::get,
            refreshPrices = {
                when (refreshCount.incrementAndGet()) {
                    1 -> firstRefresh.countDown()
                    2 -> secondRefresh.countDown()
                }
                PriceRefreshResult(requested = true, fetchedCount = 1)
            }
        )

        loop.restart()
        assertThat(firstRefresh.await(100, TimeUnit.MILLISECONDS)).isFalse()

        fixedIntervalSec.set(3L)
        loop.restart()
        assertThat(firstRefresh.await(100, TimeUnit.MILLISECONDS)).isFalse()

        pollingEnabled.set(true)

        assertThat(firstRefresh.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(secondRefresh.await(500, TimeUnit.MILLISECONDS)).isTrue()
        loop.cancel()
    }

    private fun tickerPollingLoop(
        pollingEnabled: () -> Boolean = { true },
        refreshMode: () -> AppSettingsService.RefreshMode,
        refreshPrices: suspend () -> PriceRefreshResult,
        fixedIntervalSec: () -> Long = { 3L },
        openIntervalSec: () -> Long = { 3L },
        closedIntervalSec: () -> Long = { 10L }
    ): TickerPollingLoop {
        return TickerPollingLoop(
            cs = scope,
            pollingEnabled = pollingEnabled,
            refreshMode = refreshMode,
            refreshPrices = refreshPrices,
            fixedIntervalSec = fixedIntervalSec,
            openIntervalSec = openIntervalSec,
            closedIntervalSec = closedIntervalSec,
            toDelayMillis = { it * 10L },
            errorDelayMillis = 10L
        )
    }
}
