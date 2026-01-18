package com.github.myeoungdev.marketticker.fixtures.domain

import com.github.myeoungdev.marketticker.domain.model.*

/**
 * Some Descirption...
 *
 * @author : 강명관
 * @since : 1.0
 **/
object TickerPriceFixtures {

    /**
     * TickerPrice 에 대한 기본 Factory 메서드
     */
    fun create(
        symbol: String = "AAPL",
        tradingSymbol: String = "AAPL.O",
        name: String = "Apple Inc.",
        currentPrice: Double = 150.0,
        changeRate: Double = 1.5,
        changeAmount: Double = 2.25,
        currency: CurrencyType = CurrencyType.USD,
        marketStatus: MarketStatus = MarketStatus.OPEN,
        priceStatus: PriceStatus = PriceStatus.RISING,
        marketType: MarketType = MarketType.NASDAQ,
        tradeVolume: Long = 1000000L,
        nationCode: String? = "USA"
    ): TickerPrice {
        return TickerPrice(
            symbol = symbol,
            tradingSymbol = tradingSymbol,
            name = name,
            previousClosePrice = currentPrice - changeAmount,
            openPrice = currentPrice * 0.98,
            highPrice = currentPrice * 1.02,
            lowPrice = currentPrice * 0.97,
            currentPrice = currentPrice,
            priceStatus = priceStatus,
            changeAmount = changeAmount,
            changeRate = changeRate,
            tradeVolume = tradeVolume,
            tradeValue = currentPrice * tradeVolume,
            marketStatus = marketStatus,
            marketType = marketType,
            currency = currency,
            nationCode = nationCode,
            nationName = if (nationCode == "KR") "대한민국" else "미국"
        )
    }

    /**
     * 상승 - 미국장 애플 상승세
     */
    val APPLE_RISING = create(
        symbol = "AAPL",
        currentPrice = 185.0,
        changeRate = 2.5, // +2.5%
        changeAmount = 4.5,
        priceStatus = PriceStatus.RISING,
        marketStatus = MarketStatus.OPEN
    )

    /**
     * 하락 - 미국장 테슬라 급락
     */
    val TESLA_FALLING = create(
        symbol = "TSLA",
        tradingSymbol = "TSLA.O",
        name = "Tesla Inc.",
        currentPrice = 160.0,
        changeRate = -5.5,
        changeAmount = -9.3,
        priceStatus = PriceStatus.FALLING,
        marketStatus = MarketStatus.OPEN
    )

    /**
     * 보합 - 미국장 MS 변동 없음
     */
    val MICROSOFT_STEADY = create(
        symbol = "MSFT",
        name = "Microsoft",
        currentPrice = 350.0,
        changeRate = 0.0,
        changeAmount = 0.0,
        priceStatus = PriceStatus.STEADY,
        marketStatus = MarketStatus.OPEN
    )

    /**
     * 장마감 - 장 마감 상태
     */
    val MARKET_CLOSED_PRICE = create(
        symbol = "AAPL",
        marketStatus = MarketStatus.CLOSED
    )

    /**
     * 시간외 거래 - 프리마켓/애프터마켓
     */
    val PRE_MARKET_PRICE = create(
        symbol = "NVDA",
        name = "NVIDIA",
        marketStatus = MarketStatus.EXTENDED,
        currentPrice = 490.0
    )

    /**
     * 한국장 - 삼성전자
     */
    val SAMSUNG_KRW = create(
        symbol = "005930",
        tradingSymbol = "005930",
        name = "삼성전자",
        marketType = MarketType.KOSPI,
        currency = CurrencyType.KRW,
        nationCode = "KR",
        currentPrice = 72000.0,
        changeRate = 1.2,
        changeAmount = 800.0,
        tradeVolume = 15000000L
    )

    /**
     * 거래정지 - 거래 정지 상태
     */
    val HALTED_STOCK = create(
        symbol = "RISKY",
        name = "Risky Bio",
        marketStatus = MarketStatus.HALTED,
        currentPrice = 5000.0
    )
}