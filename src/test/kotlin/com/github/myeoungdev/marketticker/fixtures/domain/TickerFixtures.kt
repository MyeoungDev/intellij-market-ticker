package com.github.myeoungdev.marketticker.fixtures.domain

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * Some Descirption...
 *
 * @author : 강명관
 * @since : 1.0
 **/
object TickerFixtures {

    /**
     * Ticker 에 대한 기본 Factory 메서드
     */
    fun create(
        symbol: String = "AAPL",
        tradingSymbol: String = "AAPL.O",
        name: String = "Apple Inc.",
        marketType: MarketType = MarketType.NASDAQ,
        nationCode: String? = "USA",
        nationName: String? = "미국"
    ): Ticker {
        return Ticker(
            symbol = symbol,
            tradingSymbol = tradingSymbol,
            name = name,
            marketType = marketType,
            nationCode = nationCode,
            nationName = nationName
        )
    }

    /**
     * NASDAQ Preset - 애플
     */
    val APPLE = create(
        symbol = "AAPL",
        tradingSymbol = "AAPL.O",
        name = "Apple Inc.",
        marketType = MarketType.NASDAQ,
        nationCode = "USA",
        nationName = "미국"
    )

    /**
     * NYSE Preset - 코카콜라
     */
    val COCA_COLA = create(
        symbol = "KO",
        tradingSymbol = "KO",
        name = "Coca-Cola Company",
        marketType = MarketType.NYSE,
        nationCode = "USA",
        nationName = "미국"
    )

    /**
     * KOSPI Preset - 삼성전자
     */
    val SAMSUNG_ELECTRONICS = create(
        symbol = "005930",
        tradingSymbol = "005930",
        name = "삼성전자",
        marketType = MarketType.KOSPI,
        nationCode = "KR",
        nationName = "대한민국"
    )

    /**
     * KOSDAQ Preset - 에코프로 BM
     */
    val ECOPRO_BM = create(
        symbol = "247540",
        tradingSymbol = "247540",
        name = "에코프로비엠",
        marketType = MarketType.KOSDAQ,
        nationCode = "KR",
        nationName = "대한민국"
    )

    /**
     * TOKYO Preset - Toyota
     */
    val TOYOTA = create(
        symbol = "7203",
        tradingSymbol = "7203.T",
        name = "Toyota Motor Corp",
        marketType = MarketType.TOKYO,
        nationCode = "JP",
        nationName = "일본"
    )

    /**
     * 데이터 누락 케이스 (국가 정보 없음)
     */
    val UNKNOWN_NATION_TICKER = create(
        symbol = "UNKNOWN",
        tradingSymbol = "UNKNOWN",
        name = "Unknown Corp",
        marketType = MarketType.UNKNOWN,
        nationCode = null,
        nationName = null
    )
}