package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.TickerPrice

data class PortfolioSummaryGroup(
    val currency: CurrencyType,
    val totalPurchaseAmount: Double,
    val totalMarketValue: Double,
    val totalProfit: Double,
    val totalReturnRate: Double,
    val dailyChangeAmount: Double?,
    val dailyChangeRate: Double?,
    val holdingCount: Int
)

data class PortfolioPosition(
    val entry: WatchlistRepository.WatchlistEntry,
    val price: TickerPrice?,
    val currency: CurrencyType,
    val purchasePrice: Double,
    val quantity: Double,
    val currentPrice: Double,
    val purchaseAmount: Double,
    val marketValue: Double,
    val unrealized: Double,
    val dailyCurrentPrice: Double?,
    val previousClosePrice: Double?
)

data class PortfolioConvertedSummary(
    val currency: CurrencyType,
    val totalPurchaseAmount: Double?,
    val totalMarketValue: Double?,
    val totalProfit: Double?,
    val totalReturnRate: Double?,
    val dailyChangeAmount: Double?,
    val dailyChangeRate: Double?,
    val holdingCount: Int,
    val convertedHoldingCount: Int,
    val excludedHoldingCount: Int
)

object PortfolioSummaryCalculator {

    fun calculate(
        entries: List<WatchlistRepository.WatchlistEntry>,
        prices: List<TickerPrice>
    ): List<PortfolioSummaryGroup> {
        return calculateSummary(projectPositions(entries, prices))
    }

    fun projectPositions(
        entries: List<WatchlistRepository.WatchlistEntry>,
        prices: List<TickerPrice>
    ): List<PortfolioPosition> {
        return entries
            .mapNotNull { entry -> entry.toPosition(prices) }
    }

    fun calculateSummary(positions: List<PortfolioPosition>): List<PortfolioSummaryGroup> {
        return positions
            .groupBy { it.currency }
            .map { (currency, positions) -> positions.toSummary(currency) }
            .sortedBy { it.currency.code }
    }

    fun calculateConvertedSummary(
        positions: List<PortfolioPosition>,
        baseCurrency: CurrencyType,
        convertAmount: (Double, CurrencyType, CurrencyType) -> Double?
    ): PortfolioConvertedSummary {
        val convertedPositions = positions.mapNotNull { position ->
            val purchaseAmount = convertAmount(position.purchaseAmount, position.currency, baseCurrency) ?: return@mapNotNull null
            val marketValue = convertAmount(position.marketValue, position.currency, baseCurrency) ?: return@mapNotNull null
            ConvertedPosition(
                purchaseAmount = purchaseAmount,
                marketValue = marketValue,
                dailyCurrentValue = position.dailyCurrentPrice
                    ?.let { convertAmount(it * position.quantity, position.currency, baseCurrency) },
                dailyPreviousValue = position.previousClosePrice
                    ?.let { convertAmount(it * position.quantity, position.currency, baseCurrency) }
            )
        }

        val totalPurchaseAmount = convertedPositions.sumOfOrNull { it.purchaseAmount }
        val totalMarketValue = convertedPositions.sumOfOrNull { it.marketValue }
        val totalProfit = if (totalPurchaseAmount != null && totalMarketValue != null) {
            totalMarketValue - totalPurchaseAmount
        } else {
            null
        }
        val totalReturnRate = if (totalPurchaseAmount != null && totalPurchaseAmount > 0.0 && totalProfit != null) {
            totalProfit / totalPurchaseAmount * 100.0
        } else {
            null
        }

        val dailyPositions = convertedPositions.filter { it.dailyCurrentValue != null && it.dailyPreviousValue != null }
        val dailyBasis = dailyPositions.sumOfOrNull { it.dailyPreviousValue!! }
        val dailyChangeAmount = if (dailyBasis != null && dailyBasis > 0.0) {
            dailyPositions.sumOf { it.dailyCurrentValue!! - it.dailyPreviousValue!! }
        } else {
            null
        }
        val dailyChangeRate = if (dailyBasis != null && dailyBasis > 0.0 && dailyChangeAmount != null) {
            dailyChangeAmount / dailyBasis * 100.0
        } else {
            null
        }

        return PortfolioConvertedSummary(
            currency = baseCurrency,
            totalPurchaseAmount = totalPurchaseAmount,
            totalMarketValue = totalMarketValue,
            totalProfit = totalProfit,
            totalReturnRate = totalReturnRate,
            dailyChangeAmount = dailyChangeAmount,
            dailyChangeRate = dailyChangeRate,
            holdingCount = positions.size,
            convertedHoldingCount = convertedPositions.size,
            excludedHoldingCount = positions.size - convertedPositions.size
        )
    }

    private fun WatchlistRepository.WatchlistEntry.toPosition(prices: List<TickerPrice>): PortfolioPosition? {
        val quantity = quantity?.takeIf { it > 0.0 && it.isFinite() } ?: return null
        val purchasePrice = purchasePrice?.takeIf { it > 0.0 && it.isFinite() } ?: return null
        val price = prices.find {
            it.symbol == symbol && it.marketType == MarketType.of(marketType)
        }
        val currentPrice = price?.currentPrice?.takeIf { it.isFinite() } ?: purchasePrice
        val currency = price?.currency ?: MarketType.of(marketType).nativeCurrency()

        return PortfolioPosition(
            entry = this,
            price = price,
            currency = currency,
            purchasePrice = purchasePrice,
            quantity = quantity,
            currentPrice = currentPrice,
            purchaseAmount = purchasePrice * quantity,
            marketValue = currentPrice * quantity,
            unrealized = (currentPrice - purchasePrice) * quantity,
            dailyCurrentPrice = price?.currentPrice?.takeIf { it.isFinite() },
            previousClosePrice = price?.previousClosePrice?.takeIf { it > 0.0 && it.isFinite() }
        )
    }

    private fun List<PortfolioPosition>.toSummary(currency: CurrencyType): PortfolioSummaryGroup {
        val totalPurchaseAmount = sumOf { it.purchaseAmount }
        val totalMarketValue = sumOf { it.marketValue }
        val totalProfit = totalMarketValue - totalPurchaseAmount
        val totalReturnRate = if (totalPurchaseAmount > 0.0) {
            totalProfit / totalPurchaseAmount * 100.0
        } else {
            0.0
        }

        val dailyPositions = filter { it.dailyCurrentPrice != null && it.previousClosePrice != null }
        val dailyBasis = dailyPositions.sumOf { it.previousClosePrice!! * it.quantity }
        val dailyChangeAmount = if (dailyBasis > 0.0) {
            dailyPositions.sumOf { (it.dailyCurrentPrice!! - it.previousClosePrice!!) * it.quantity }
        } else {
            null
        }
        val dailyChangeRate = dailyChangeAmount?.let { it / dailyBasis * 100.0 }

        return PortfolioSummaryGroup(
            currency = currency,
            totalPurchaseAmount = totalPurchaseAmount,
            totalMarketValue = totalMarketValue,
            totalProfit = totalProfit,
            totalReturnRate = totalReturnRate,
            dailyChangeAmount = dailyChangeAmount,
            dailyChangeRate = dailyChangeRate,
            holdingCount = size
        )
    }

    private data class ConvertedPosition(
        val purchaseAmount: Double,
        val marketValue: Double,
        val dailyCurrentValue: Double?,
        val dailyPreviousValue: Double?
    )

    private fun <T> List<T>.sumOfOrNull(selector: (T) -> Double): Double? {
        return takeIf { it.isNotEmpty() }?.sumOf(selector)
    }
}
