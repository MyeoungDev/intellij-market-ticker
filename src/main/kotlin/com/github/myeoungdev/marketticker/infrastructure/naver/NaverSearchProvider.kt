package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.SearchProvider
import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * Naver 검색 응답을 플러그인 종목 모델로 변환하는 provider 구현체입니다.
 */
class NaverSearchProvider(
    private val naverClient: NaverClient = NaverClient()
) : SearchProvider {

    override fun search(query: String): List<Ticker> {
        val searchStocks = naverClient.searchStocks(query)
        return searchStocks.map { stock -> stock.toTicker() }
    }
}
