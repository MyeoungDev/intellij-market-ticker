package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.infrastructure.naver.NaverClient
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverNewsProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverPriceProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverResearchProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchProvider

/**
 * 현재 기본 데이터 소스 구현체를 조립하는 레지스트리입니다.
 */
object DefaultDataSourceRegistry {

    private val naverClient: NaverClient by lazy { NaverClient() }
    private val priceProvider: PriceProvider by lazy { NaverPriceProvider(naverClient) }
    private val searchProvider: SearchProvider by lazy { NaverSearchProvider(naverClient) }
    private val newsProvider: NewsProvider by lazy { NaverNewsProvider(naverClient) }
    private val researchProvider: ResearchProvider by lazy { NaverResearchProvider(naverClient) }

    fun priceProvider(): PriceProvider = priceProvider

    fun searchProvider(): SearchProvider = searchProvider

    fun newsProvider(): NewsProvider = newsProvider

    fun researchProvider(): ResearchProvider = researchProvider
}
