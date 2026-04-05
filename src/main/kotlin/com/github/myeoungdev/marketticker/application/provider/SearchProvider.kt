package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * 종목 검색 데이터를 공급하는 provider 인터페이스입니다.
 */
interface SearchProvider {
    fun search(query: String): List<Ticker>
}
