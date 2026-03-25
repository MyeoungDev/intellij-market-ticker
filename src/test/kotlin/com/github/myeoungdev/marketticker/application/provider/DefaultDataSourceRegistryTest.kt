package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.infrastructure.naver.NaverNewsProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverPriceProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverResearchProvider
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultDataSourceRegistryTest {

    @Test
    fun `기본 provider 조립은 Naver 구현체를 반환한다`() {
        assertThat(DefaultDataSourceRegistry.priceProvider()).isInstanceOf(NaverPriceProvider::class.java)
        assertThat(DefaultDataSourceRegistry.searchProvider()).isInstanceOf(NaverSearchProvider::class.java)
        assertThat(DefaultDataSourceRegistry.newsProvider()).isInstanceOf(NaverNewsProvider::class.java)
        assertThat(DefaultDataSourceRegistry.researchProvider()).isInstanceOf(NaverResearchProvider::class.java)
    }

    @Test
    fun `같은 provider 인스턴스를 재사용한다`() {
        assertThat(DefaultDataSourceRegistry.priceProvider()).isSameAs(DefaultDataSourceRegistry.priceProvider())
        assertThat(DefaultDataSourceRegistry.searchProvider()).isSameAs(DefaultDataSourceRegistry.searchProvider())
        assertThat(DefaultDataSourceRegistry.newsProvider()).isSameAs(DefaultDataSourceRegistry.newsProvider())
        assertThat(DefaultDataSourceRegistry.researchProvider()).isSameAs(DefaultDataSourceRegistry.researchProvider())
    }
}
