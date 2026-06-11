package com.github.myeoungdev.marketticker.application.repository

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WatchlistRepositoryTest {

    private lateinit var service: WatchlistRepository

    private val DEFAULT_TICKER = Ticker(
        "005930",
        "005930",
        "삼성전자",
        MarketType.KOSPI,
        "KOR",
        "대한민국"
    )

    @BeforeEach
    fun setUp() {
        service = WatchlistRepository()
    }

    @Test
    fun `초기 상태는 비어있다`() {
        assertTrue(service.getWatchlistEntries().isEmpty())
    }

    @Test
    fun `종목을 추가하면 리스트에 저장된다`() {
        // Given & When
        service.addTicker(DEFAULT_TICKER)

        // Then
        val list = service.getWatchlistEntries()
        assertEquals(1, list.size)
        assertEquals(DEFAULT_TICKER.name, list[0].name)
    }


    @Test
    fun `이미 존재하는 종목은 중복해서 추가되지 않는다`() {
        // Given
        val ticker = DEFAULT_TICKER
        service.addTicker(ticker)

        // When (같은 심볼 추가 시도)
        service.addTicker(
            DEFAULT_TICKER.copy(
                name = "삼성전자(중복)"
            )
        )

        // Then
        val list = service.getWatchlistEntries()
        assertEquals(1, list.size)
        assertEquals(DEFAULT_TICKER.name, list[0].name)
    }

    @Test
    fun `종목 삭제가 정상적으로 동작한다`() {
        // Given
        val t1 = DEFAULT_TICKER
        val t2 = DEFAULT_TICKER.copy(
            symbol = "NVDA",
            name = "NVIDIA",
            marketType = MarketType.NASDAQ
        )

        service.addTicker(t1)
        service.addTicker(t2)

        // When
        service.removeTicker(t1.symbol)

        // Then
        val list = service.getWatchlistEntries()
        assertEquals(1, list.size)
        assertEquals(t2.name, list[0].name)
    }

    @Test
    fun `같은 symbol이라도 marketType을 지정해 삭제하면 해당 시장만 삭제된다`() {
        val kr = DEFAULT_TICKER.copy(symbol = "ABC", marketType = MarketType.KOSPI)
        val us = DEFAULT_TICKER.copy(symbol = "ABC", marketType = MarketType.NASDAQ, tradingSymbol = "ABC.O")

        service.addTicker(kr)
        service.addTicker(us)

        service.removeTicker("ABC", MarketType.KOSPI.name)

        val list = service.getWatchlistEntries()
        assertEquals(1, list.size)
        assertEquals(MarketType.NASDAQ.name, list[0].marketType)
    }

    @Test
    fun `XML 저장용 상태 객체(State) 로드 테스트`() {
        // Given
        val loadedState = WatchlistRepository.State()
        loadedState.tickers.add(
            WatchlistRepository.WatchlistEntry(
                DEFAULT_TICKER.symbol,
                DEFAULT_TICKER.tradingSymbol,
                DEFAULT_TICKER.name,
                DEFAULT_TICKER.marketType.name,
                DEFAULT_TICKER.nationCode,
                DEFAULT_TICKER.nationName
            )
        )

        // When
        service.loadState(loadedState)

        // Then
        val list = service.getWatchlistEntries()
        assertEquals(1, list.size)
        assertEquals(DEFAULT_TICKER.name, list[0].name)
    }

    @Test
    fun `저장 상태 로드시 비어있는 tradingSymbol 과 groupTag 를 보정한다`() {
        val loadedState = WatchlistRepository.State()
        loadedState.tickers.add(
            WatchlistRepository.WatchlistEntry(
                symbol = DEFAULT_TICKER.symbol,
                tradingSymbol = "",
                name = DEFAULT_TICKER.name,
                marketType = DEFAULT_TICKER.marketType.name,
                nationCode = DEFAULT_TICKER.nationCode,
                nationName = DEFAULT_TICKER.nationName,
                groupTag = ""
            )
        )

        service.loadState(loadedState)

        val entry = service.getWatchlistEntries().single()
        assertEquals(DEFAULT_TICKER.symbol, entry.tradingSymbol)
        assertEquals("국내", entry.groupTag)
    }

    @Test
    fun `getWatchlistEntries 는 내부 상태의 복사본을 반환한다`() {
        service.addTicker(DEFAULT_TICKER)

        val first = service.getWatchlistEntries().single()
        first.name = "변경"

        val second = service.getWatchlistEntries().single()
        assertEquals(DEFAULT_TICKER.name, second.name)
    }

    @Test
    fun `포트폴리오 삭제는 관심종목을 유지하고 포트폴리오 필드만 비운다`() {
        service.addTicker(DEFAULT_TICKER)
        service.updateWatchlistEntryPortfolio(
            service.getWatchlistEntries().single().copy(
                purchasePrice = 1000.0,
                quantity = 3.0,
                targetWeightPercentage = 25.0,
                realizedProfitLoss = 10.0
            )
        )

        service.clearPortfolio(DEFAULT_TICKER.symbol, DEFAULT_TICKER.marketType.name)

        val entry = service.getWatchlistEntries().single()
        assertEquals(DEFAULT_TICKER.symbol, entry.symbol)
        assertEquals(null, entry.purchasePrice)
        assertEquals(null, entry.quantity)
        assertEquals(null, entry.targetWeightPercentage)
        assertEquals(0.0, entry.realizedProfitLoss)
    }

    @Test
    fun `종목을 위로 이동하면 저장 순서가 변경된다`() {
        service.addTicker(DEFAULT_TICKER)
        service.addTicker(ticker("000660", "SK하이닉스", MarketType.KOSPI))
        service.addTicker(ticker("NVDA", "NVIDIA", MarketType.NASDAQ))

        assertTrue(service.moveTicker("NVDA", MarketType.NASDAQ.name, 0))

        assertEquals(
            listOf("NVDA", "005930", "000660"),
            service.getWatchlistEntries().map { it.symbol }
        )
    }

    @Test
    fun `종목을 아래로 이동하면 저장 순서가 변경된다`() {
        service.addTicker(DEFAULT_TICKER)
        service.addTicker(ticker("000660", "SK하이닉스", MarketType.KOSPI))
        service.addTicker(ticker("NVDA", "NVIDIA", MarketType.NASDAQ))

        assertTrue(service.moveTicker("005930", MarketType.KOSPI.name, 3))

        assertEquals(
            listOf("000660", "NVDA", "005930"),
            service.getWatchlistEntries().map { it.symbol }
        )
    }

    @Test
    fun `같은 위치로 이동하면 순서를 변경하지 않는다`() {
        service.addTicker(DEFAULT_TICKER)
        service.addTicker(ticker("000660", "SK하이닉스", MarketType.KOSPI))

        assertFalse(service.moveTicker("005930", MarketType.KOSPI.name, 1))

        assertEquals(
            listOf("005930", "000660"),
            service.getWatchlistEntries().map { it.symbol }
        )
    }

    @Test
    fun `존재하지 않는 종목 이동은 순서를 변경하지 않는다`() {
        service.addTicker(DEFAULT_TICKER)

        assertFalse(service.moveTicker("MISSING", MarketType.KOSPI.name, 0))

        assertEquals(listOf("005930"), service.getWatchlistEntries().map { it.symbol })
    }

    @Test
    fun `같은 symbol 이라도 marketType 이 다른 종목은 구분해서 이동한다`() {
        service.addTicker(ticker("ABC", "ABC Korea", MarketType.KOSPI))
        service.addTicker(ticker("ABC", "ABC USA", MarketType.NASDAQ, tradingSymbol = "ABC.O"))
        service.addTicker(ticker("NVDA", "NVIDIA", MarketType.NASDAQ))

        assertTrue(service.moveTicker("ABC", MarketType.NASDAQ.name, 0))

        assertEquals(
            listOf(MarketType.NASDAQ.name, MarketType.KOSPI.name, MarketType.NASDAQ.name),
            service.getWatchlistEntries().map { it.marketType }
        )
        assertEquals("ABC.O", service.getWatchlistEntries().first().tradingSymbol)
    }

    @Test
    fun `종목 이동은 포트폴리오와 그룹 메타데이터를 유지한다`() {
        service.addTicker(DEFAULT_TICKER)
        service.addTicker(ticker("NVDA", "NVIDIA", MarketType.NASDAQ))
        service.updateWatchlistEntryPortfolio(
            service.getWatchlistEntries().first { it.symbol == "005930" }.copy(
                purchasePrice = 70_000.0,
                quantity = 2.0,
                targetWeightPercentage = 30.0,
                realizedProfitLoss = 1_000.0,
                groupTag = "반도체"
            )
        )

        assertTrue(service.moveTicker("005930", MarketType.KOSPI.name, 2))

        val moved = service.getWatchlistEntries().last()
        assertEquals("005930", moved.symbol)
        assertEquals(70_000.0, moved.purchasePrice)
        assertEquals(2.0, moved.quantity)
        assertEquals(30.0, moved.targetWeightPercentage)
        assertEquals(1_000.0, moved.realizedProfitLoss)
        assertEquals("반도체", moved.groupTag)
    }

    private fun ticker(
        symbol: String,
        name: String,
        marketType: MarketType,
        tradingSymbol: String = symbol
    ): Ticker {
        return DEFAULT_TICKER.copy(
            symbol = symbol,
            tradingSymbol = tradingSymbol,
            name = name,
            marketType = marketType
        )
    }

}
