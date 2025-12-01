package com.github.myeoungdev.marketticker.application.watch

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
        // Given
        val ticker = Ticker("005930", "삼성전자", "KOSPI", "KOR")

        // When
        service.addTicker(ticker)

        // Then
        val list = service.getWatchlist()
        assertEquals(1, list.size)
        assertEquals("삼성전자", list[0].name)
    }


    @Test
    fun `이미 존재하는 종목은 중복해서 추가되지 않는다`() {
        // Given
        val ticker = Ticker("005930", "삼성전자", "KOSPI", "KOR")
        service.addTicker(ticker)

        // When (같은 심볼 추가 시도)
        service.addTicker(Ticker("005930", "삼성전자(중복)", "KOSPI", "KOR"))

        // Then
        val list = service.getWatchlist()
        assertEquals(1, list.size)
        assertEquals("삼성전자", list[0].name)
    }

    @Test
    fun `종목 삭제가 정상적으로 동작한다`() {
        // Given
        val t1 = Ticker("005930", "삼성전자", "KOSPI", "KOR")
        val t2 = Ticker("NVDA", "NVIDIA", "NASDAQ", "USA")
        service.addTicker(t1)
        service.addTicker(t2)

        // When
        service.removeTicker("005930")

        // Then
        val list = service.getWatchlist()
        assertEquals(1, list.size)
        assertEquals("NVIDIA", list[0].name)
    }

    @Test
    fun `XML 저장용 상태 객체(State) 로드 테스트`() {
        // Given: 외부에서 XML 로드된 상황 시뮬레이션
        val loadedState = WatchlistDataService.State()
        loadedState.tickers.add(Ticker("MSFT", "Microsoft", "NASDAQ", "USA"))

        // When
        service.loadState(loadedState)

        // Then
        val list = service.getWatchlist()
        assertEquals(1, list.size)
        assertEquals("Microsoft", list[0].name)
    }

}