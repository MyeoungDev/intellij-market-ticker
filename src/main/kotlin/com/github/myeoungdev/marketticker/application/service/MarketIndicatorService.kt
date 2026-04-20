package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.application.provider.MarketIndicatorProvider
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 주요 지수/원자재 지표를 주기적으로 조회해 제공하는 서비스입니다.
 */
@Service(Service.Level.APP)
class MarketIndicatorService(
    private val cs: CoroutineScope
) {
    private val marketIndicatorProvider: MarketIndicatorProvider = DefaultDataSourceRegistry.marketIndicatorProvider()

    private val _indicators = MutableStateFlow<List<MarketIndicator>>(emptyList())
    val indicators: StateFlow<List<MarketIndicator>> = _indicators.asStateFlow()

    init {
        startPolling()
    }

    private fun startPolling() {
        cs.launch {
            while (isActive) {
                refresh()
                delay(70_000L)
            }
        }
    }

    /**
     * 지표 목록을 즉시 갱신합니다.
     */
    fun refresh() {
        cs.launch {
            _indicators.emit(marketIndicatorProvider.getIndicators())
        }
    }
}
