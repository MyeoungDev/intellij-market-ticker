package com.github.myeoungdev.marketticker.application.search

import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-02
 */
interface SearchProvider {
    fun search(query: String): List<Ticker>
}