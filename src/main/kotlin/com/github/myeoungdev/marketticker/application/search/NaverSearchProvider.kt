package com.github.myeoungdev.marketticker.application.search

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverClient

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-02
 */
class NaverSearchProvider(
    private val naverClient: NaverClient = NaverClient()
) : SearchProvider {

    override fun search(query: String): List<Ticker> {
        val searchStocks = naverClient.searchStocks(query)
        print("검색 결과: $searchStocks")

        return searchStocks.map { stock -> stock.toTicker() }
    }
}