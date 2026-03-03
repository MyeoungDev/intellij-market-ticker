package com.github.myeoungdev.marketticker.ui.statusbar

import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.common.extenion.toCommaString
import com.github.myeoungdev.marketticker.domain.model.PriceStatus
import com.github.myeoungdev.marketticker.domain.model.TickerPrice
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.awt.event.MouseEvent

private val logger = KotlinLogging.logger {}

class MarketTickerStatusBarWidget(
    private val project: Project
) : StatusBarWidget, StatusBarWidget.TextPresentation {

    companion object {
        const val ID = "MarketTickerStatusBarWidget"
    }

    private var statusBar: StatusBar? = null

    private val marketDataService = service<MarketDataService>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var prices: List<TickerPrice> = emptyList()

    @Volatile
    private var index: Int = 0

    private var collectJob: Job? = null
    private var rotateJob: Job? = null

    override fun ID(): String = ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun getAlignment(): Float = 0.5f

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar

        // 1) 가격 스트림 구독
        collectJob = scope.launch {
            logger.info { "Start collecting prices..." }
            marketDataService.currentPrices.collect { newPrices ->
                logger.info { "Received prices: ${prices.size}" }

                prices = newPrices
                if (index >= prices.size) index = 0
                updateWidget()
            }
        }

        // 2) 로테이션 루프 (표시만 바꿈)
        rotateJob = scope.launch {
            while (isActive) {
                if (prices.isNotEmpty()) {
                    index = (index + 1) % prices.size
                    updateWidget()
                }
                delay(3_000L)
            }
        }
    }

    override fun dispose() {
        collectJob?.cancel()
        rotateJob?.cancel()
        scope.cancel()
        statusBar = null
    }

    override fun getText(): String {
        val list = prices
        if (list.isEmpty()) return "Market Ticker: watchlist empty"

        val tickerPrice = list.getOrNull(index) ?: return "Market Ticker: loading..."

        val arrow = when (tickerPrice.priceStatus) {
            PriceStatus.RISING -> "▲"
            PriceStatus.FALLING -> "▼"
            PriceStatus.STEADY -> "–"
        }

        val sign = if (tickerPrice.changeRate > 0) "+" else ""
        val currency = tickerPrice.currency.name

        // 예: "AAPL 195.12 USD ▲ (+1.23%)"
        return "${tickerPrice.name} " +
                "${tickerPrice.currentPrice.toCommaString()} " +
                "$currency " +
                "$arrow " +
                "($sign${tickerPrice.changeRate.toCommaString()}%)"
    }

    override fun getTooltipText(): String = "Market Ticker (click to open)"

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer {
        ToolWindowManager.getInstance(project).getToolWindow("Market Ticker")?.show {
            logger.info { "Market Ticker ToolWindow Open" }
        }
    }

    private fun updateWidget() {
        ApplicationManager.getApplication().invokeLater {
            statusBar?.updateWidget(ID)
        }
    }

}
