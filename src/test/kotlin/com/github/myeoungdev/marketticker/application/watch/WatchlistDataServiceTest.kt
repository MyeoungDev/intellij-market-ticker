package com.github.myeoungdev.marketticker.application.watch

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 사용자가 등록해둔 관심 종목에 대해서 관리하는 서비스 클래스입니다.
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
class WatchlistDataServiceTest {

    private lateinit var service: WatchlistDataService

    private val DEFAULT_TICKER = Ticker(
        "005930",
        "삼성전자",
        MarketType.KOSPI,
        "KOR",
        "대한민국"
    )

    @Before
    fun setUp() {
        service = WatchlistDataService()
    }

    @Test
    fun `초기 상태는 비어있다`() {
        assertTrue(service.getWatchlist().isEmpty())
    }

    @Test
    fun `종목을 추가하면 리스트에 저장된다`() {
        // Given & When
        service.addTicker(DEFAULT_TICKER)

        // Then
        val list = service.getWatchlist()
        assertEquals(1, list.size)
        assertEquals(DEFAULT_TICKER.name, list[0].name)
    }


    @Test
    fun `이미 존재하는 종목은 중복해서 추가되지 않는다`() {
        // Given
        val ticker = DEFAULT_TICKER
        service.addTicker(ticker)

        // When (같은 심볼 추가 시도)
        service.addTicker(DEFAULT_TICKER.copy(
            name = "삼성전자(중복)"
        ))

        // Then
        val list = service.getWatchlist()
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
            MarketType.NASDAQ
        )

        service.addTicker(t1)
        service.addTicker(t2)

        // When
        service.removeTicker(t1.symbol)

        // Then
        val list = service.getWatchlist()
        assertEquals(1, list.size)
        assertEquals(t2.name, list[0].name)
    }

    @Test
    fun `XML 저장용 상태 객체(State) 로드 테스트`() {
        // Given
        val loadedState = WatchlistDataService.State()
        loadedState.tickers.add(DEFAULT_TICKER)

        // When
        service.loadState(loadedState)

        // Then
        val list = service.getWatchlist()
        assertEquals(1, list.size)
        assertEquals(DEFAULT_TICKER.name, list[0].name)
    }

}