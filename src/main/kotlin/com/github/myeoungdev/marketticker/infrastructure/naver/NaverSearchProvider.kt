package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.SearchProvider
import com.github.myeoungdev.marketticker.domain.model.Ticker

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
        return searchStocks.map { stock -> stock.toTicker() }
    }
}
