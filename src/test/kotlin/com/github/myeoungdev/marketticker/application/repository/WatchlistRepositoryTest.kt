package com.github.myeoungdev.marketticker.application.repository

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    @Before
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

}
