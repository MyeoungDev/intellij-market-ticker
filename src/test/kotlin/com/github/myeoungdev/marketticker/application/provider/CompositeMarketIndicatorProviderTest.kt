package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CompositeMarketIndicatorProviderTest {

    @Test
    fun `providers 는 병렬로 실행된다`() {
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val provider = CompositeMarketIndicatorProvider(
                listOf(
                    blockingProvider(started, release, "A"),
                    blockingProvider(started, release, "B")
                )
            )

            val future = executor.submit<List<MarketIndicator>> { provider.getIndicators() }

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue()
            release.countDown()

            val result = future.get(1, TimeUnit.SECONDS)
            assertThat(result.map { it.code }).containsExactly("A", "B")
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `한 provider 가 실패해도 다른 provider 결과는 유지된다`() {
        val provider = CompositeMarketIndicatorProvider(
            listOf(
                object : MarketIndicatorProvider {
                    override fun getIndicators(): List<MarketIndicator> {
                        throw IllegalStateException("boom")
                    }
                },
                object : MarketIndicatorProvider {
                    override fun getIndicators(): List<MarketIndicator> {
                        return listOf(indicator("B"))
                    }
                }
            )
        )

        val result = provider.getIndicators()

        assertThat(result.map { it.code }).containsExactly("B")
    }

    private fun blockingProvider(
        started: CountDownLatch,
        release: CountDownLatch,
        code: String
    ): MarketIndicatorProvider {
        return object : MarketIndicatorProvider {
            override fun getIndicators(): List<MarketIndicator> {
                started.countDown()
                assertThat(started.await(1, TimeUnit.SECONDS)).isTrue()
                release.await(1, TimeUnit.SECONDS)
                return listOf(indicator(code))
            }
        }
    }

    private fun indicator(code: String): MarketIndicator {
        return MarketIndicator(
            code = code,
            name = code,
            currentPrice = 1.0,
            changeRate = 0.0,
            marketStatus = MarketStatus.OPEN,
            category = IndicatorCategory.WORLD_INDEX,
            unit = null
        )
    }
}
