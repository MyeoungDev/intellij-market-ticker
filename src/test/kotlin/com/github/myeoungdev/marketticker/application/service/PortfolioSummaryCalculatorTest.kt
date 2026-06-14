package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.fixtures.domain.TickerPriceFixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test

class PortfolioSummaryCalculatorTest {

    @Test
    fun `보유 종목 요약을 통화별로 계산한다`() {
        val entries = listOf(
            entry(symbol = "005930", marketType = MarketType.KOSPI, purchasePrice = 70_000.0, quantity = 2.0),
            entry(symbol = "AAPL", marketType = MarketType.NASDAQ, purchasePrice = 100.0, quantity = 10.0)
        )
        val prices = listOf(
            TickerPriceFixtures.create(
                symbol = "005930",
                marketType = MarketType.KOSPI,
                currentPrice = 72_000.0,
                changeAmount = 800.0,
                currency = CurrencyType.KRW
            ),
            TickerPriceFixtures.create(
                symbol = "AAPL",
                marketType = MarketType.NASDAQ,
                currentPrice = 120.0,
                changeAmount = 20.0,
                currency = CurrencyType.USD
            )
        )

        val result = PortfolioSummaryCalculator.calculate(entries, prices)

        val krw = result.single { it.currency == CurrencyType.KRW }
        assertThat(krw.totalPurchaseAmount).isEqualTo(140_000.0)
        assertThat(krw.totalMarketValue).isEqualTo(144_000.0)
        assertThat(krw.totalProfit).isEqualTo(4_000.0)
        assertThat(krw.totalReturnRate).isCloseTo(2.8571, offset(0.0001))
        assertThat(krw.dailyChangeAmount).isEqualTo(1_600.0)
        assertThat(krw.dailyChangeRate).isCloseTo(1.1235, offset(0.0001))
        assertThat(krw.holdingCount).isEqualTo(1)

        val usd = result.single { it.currency == CurrencyType.USD }
        assertThat(usd.totalPurchaseAmount).isEqualTo(1_000.0)
        assertThat(usd.totalMarketValue).isEqualTo(1_200.0)
        assertThat(usd.totalProfit).isEqualTo(200.0)
        assertThat(usd.totalReturnRate).isEqualTo(20.0)
        assertThat(usd.dailyChangeAmount).isEqualTo(200.0)
        assertThat(usd.dailyChangeRate).isEqualTo(20.0)
        assertThat(usd.holdingCount).isEqualTo(1)
    }

    @Test
    fun `최신 가격이 없으면 시장 통화와 매입가를 평가금액에 사용하고 일일 변동에서는 제외한다`() {
        val entries = listOf(
            entry(symbol = "MISSING", marketType = MarketType.NASDAQ, purchasePrice = 50.0, quantity = 2.0)
        )

        val result = PortfolioSummaryCalculator.calculate(entries, emptyList())

        val summary = result.single()
        assertThat(summary.currency).isEqualTo(CurrencyType.USD)
        assertThat(summary.totalPurchaseAmount).isEqualTo(100.0)
        assertThat(summary.totalMarketValue).isEqualTo(100.0)
        assertThat(summary.totalProfit).isEqualTo(0.0)
        assertThat(summary.totalReturnRate).isEqualTo(0.0)
        assertThat(summary.dailyChangeAmount).isNull()
        assertThat(summary.dailyChangeRate).isNull()
        assertThat(summary.holdingCount).isEqualTo(1)
    }

    @Test
    fun `가격이 없는 여러 시장 보유분은 시장 통화별로 분리한다`() {
        val entries = listOf(
            entry(symbol = "005930", marketType = MarketType.KOSPI, purchasePrice = 70_000.0, quantity = 1.0),
            entry(symbol = "AAPL", marketType = MarketType.NASDAQ, purchasePrice = 100.0, quantity = 1.0)
        )

        val result = PortfolioSummaryCalculator.calculate(entries, emptyList())

        assertThat(result.map { it.currency }).containsExactly(CurrencyType.KRW, CurrencyType.USD)
        assertThat(result.single { it.currency == CurrencyType.KRW }.totalMarketValue).isEqualTo(70_000.0)
        assertThat(result.single { it.currency == CurrencyType.USD }.totalMarketValue).isEqualTo(100.0)
    }

    @Test
    fun `position projection은 요약과 테이블이 공유할 평가값을 계산한다`() {
        val entries = listOf(
            entry(symbol = "AAPL", marketType = MarketType.NASDAQ, purchasePrice = 100.0, quantity = 2.0)
        )
        val prices = listOf(
            TickerPriceFixtures.create(
                symbol = "AAPL",
                marketType = MarketType.NASDAQ,
                currentPrice = 120.0,
                changeAmount = 5.0,
                currency = CurrencyType.USD
            )
        )

        val position = PortfolioSummaryCalculator.projectPositions(entries, prices).single()

        assertThat(position.purchaseAmount).isEqualTo(200.0)
        assertThat(position.marketValue).isEqualTo(240.0)
        assertThat(position.unrealized).isEqualTo(40.0)
        assertThat(position.currency).isEqualTo(CurrencyType.USD)
    }

    @Test
    fun `전일 종가가 유효하지 않은 종목은 일일 변동 계산에서 제외한다`() {
        val entries = listOf(
            entry(symbol = "AAPL", marketType = MarketType.NASDAQ, purchasePrice = 100.0, quantity = 3.0)
        )
        val prices = listOf(
            TickerPriceFixtures.create(
                symbol = "AAPL",
                marketType = MarketType.NASDAQ,
                currentPrice = 120.0,
                changeAmount = 120.0,
                currency = CurrencyType.USD
            )
        )

        val result = PortfolioSummaryCalculator.calculate(entries, prices)

        val summary = result.single()
        assertThat(summary.totalMarketValue).isEqualTo(360.0)
        assertThat(summary.dailyChangeAmount).isNull()
        assertThat(summary.dailyChangeRate).isNull()
    }

    @Test
    fun `매입가나 수량이 유효하지 않은 행은 보유 종목에서 제외한다`() {
        val entries = listOf(
            entry(symbol = "VALID", purchasePrice = 10.0, quantity = 1.0),
            entry(symbol = "NO_PRICE", purchasePrice = null, quantity = 1.0),
            entry(symbol = "NO_QUANTITY", purchasePrice = 10.0, quantity = null),
            entry(symbol = "ZERO_PRICE", purchasePrice = 0.0, quantity = 1.0),
            entry(symbol = "ZERO_QUANTITY", purchasePrice = 10.0, quantity = 0.0)
        )

        val result = PortfolioSummaryCalculator.calculate(entries, emptyList())

        assertThat(result).hasSize(1)
        assertThat(result.single().holdingCount).isEqualTo(1)
        assertThat(result.single().totalPurchaseAmount).isEqualTo(10.0)
    }

    @Test
    fun `기준 통화 summary는 환산된 금액으로 합산한다`() {
        val entries = listOf(
            entry(symbol = "005930", marketType = MarketType.KOSPI, purchasePrice = 70_000.0, quantity = 2.0),
            entry(symbol = "AAPL", marketType = MarketType.NASDAQ, purchasePrice = 100.0, quantity = 10.0)
        )
        val prices = listOf(
            TickerPriceFixtures.create(
                symbol = "005930",
                marketType = MarketType.KOSPI,
                currentPrice = 72_000.0,
                changeAmount = 2_000.0,
                currency = CurrencyType.KRW
            ),
            TickerPriceFixtures.create(
                symbol = "AAPL",
                marketType = MarketType.NASDAQ,
                currentPrice = 120.0,
                changeAmount = 20.0,
                currency = CurrencyType.USD
            )
        )
        val positions = PortfolioSummaryCalculator.projectPositions(entries, prices)

        val result = PortfolioSummaryCalculator.calculateConvertedSummary(
            positions = positions,
            baseCurrency = CurrencyType.KRW,
            convertAmount = { value, currency, _ -> if (currency == CurrencyType.USD) value * 1_400.0 else value }
        )

        assertThat(result.currency).isEqualTo(CurrencyType.KRW)
        assertThat(result.totalPurchaseAmount).isEqualTo(1_540_000.0)
        assertThat(result.totalMarketValue).isEqualTo(1_824_000.0)
        assertThat(result.totalProfit).isEqualTo(284_000.0)
        assertThat(result.totalReturnRate).isCloseTo(18.4415, offset(0.0001))
        assertThat(result.dailyChangeAmount).isEqualTo(284_000.0)
        assertThat(result.dailyChangeRate).isCloseTo(18.4415, offset(0.0001))
        assertThat(result.holdingCount).isEqualTo(2)
        assertThat(result.convertedHoldingCount).isEqualTo(2)
        assertThat(result.excludedHoldingCount).isEqualTo(0)
    }

    @Test
    fun `기준 통화로 환산할 수 없는 보유분은 합산에서 제외한다`() {
        val entries = listOf(
            entry(symbol = "005930", marketType = MarketType.KOSPI, purchasePrice = 70_000.0, quantity = 1.0),
            entry(symbol = "AAPL", marketType = MarketType.NASDAQ, purchasePrice = 100.0, quantity = 1.0)
        )
        val positions = PortfolioSummaryCalculator.projectPositions(entries, emptyList())

        val result = PortfolioSummaryCalculator.calculateConvertedSummary(
            positions = positions,
            baseCurrency = CurrencyType.KRW,
            convertAmount = { value, currency, _ -> if (currency == CurrencyType.KRW) value else null }
        )

        assertThat(result.totalPurchaseAmount).isEqualTo(70_000.0)
        assertThat(result.totalMarketValue).isEqualTo(70_000.0)
        assertThat(result.totalProfit).isEqualTo(0.0)
        assertThat(result.holdingCount).isEqualTo(2)
        assertThat(result.convertedHoldingCount).isEqualTo(1)
        assertThat(result.excludedHoldingCount).isEqualTo(1)
    }

    @Test
    fun `환산 가능한 보유분이 없으면 금액 summary는 비어 있고 제외 수만 표시한다`() {
        val positions = PortfolioSummaryCalculator.projectPositions(
            entries = listOf(entry(symbol = "AAPL", marketType = MarketType.NASDAQ, purchasePrice = 100.0, quantity = 1.0)),
            prices = emptyList()
        )

        val result = PortfolioSummaryCalculator.calculateConvertedSummary(
            positions = positions,
            baseCurrency = CurrencyType.KRW,
            convertAmount = { _, _, _ -> null }
        )

        assertThat(result.totalPurchaseAmount).isNull()
        assertThat(result.totalMarketValue).isNull()
        assertThat(result.totalProfit).isNull()
        assertThat(result.totalReturnRate).isNull()
        assertThat(result.dailyChangeAmount).isNull()
        assertThat(result.dailyChangeRate).isNull()
        assertThat(result.holdingCount).isEqualTo(1)
        assertThat(result.convertedHoldingCount).isEqualTo(0)
        assertThat(result.excludedHoldingCount).isEqualTo(1)
    }

    private fun entry(
        symbol: String,
        marketType: MarketType = MarketType.NASDAQ,
        purchasePrice: Double?,
        quantity: Double?
    ): WatchlistRepository.WatchlistEntry {
        return WatchlistRepository.WatchlistEntry(
            symbol = symbol,
            tradingSymbol = symbol,
            name = symbol,
            marketType = marketType.name,
            purchasePrice = purchasePrice,
            quantity = quantity
        )
    }
}
