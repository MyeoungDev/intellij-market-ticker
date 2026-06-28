package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 여러 시장 지표 provider 를 순서대로 합칩니다.
 */
class CompositeMarketIndicatorProvider(
    private val providers: List<MarketIndicatorProvider>,
    private val providerTimeout: Duration = Duration.ofSeconds(3)
) : MarketIndicatorProvider {

    private val logger = KotlinLogging.logger {}

    override fun getIndicators(): List<MarketIndicator> {
        val futures = providers.mapIndexed { index, provider ->
            CompletableFuture.supplyAsync {
                runCatching { provider.getIndicators() }
                    .onFailure { logger.error(it) { "Failed to fetch market indicators from provider[$index]" } }
                    .getOrDefault(emptyList())
            }.orTimeout(providerTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally { throwable ->
                    logger.error(throwable) { "Failed to fetch market indicators from provider[$index] within ${providerTimeout.toMillis()}ms" }
                    emptyList()
                }
        }

        return futures.flatMap { it.join() }
    }
}
