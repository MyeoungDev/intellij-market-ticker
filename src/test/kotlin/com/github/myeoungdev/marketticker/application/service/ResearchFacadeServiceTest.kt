package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.provider.ResearchProvider
import com.github.myeoungdev.marketticker.application.provider.SearchProvider
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.research.ResearchArticle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchCategory
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingBundle
import com.github.myeoungdev.marketticker.domain.model.research.ResearchRankingType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

class ResearchFacadeServiceTest {

    @Test
    fun `리서치 홈은 TTL 내에서 캐시를 재사용한다`() = runBlocking {
        val provider = FakeResearchProvider()
        val service = ResearchFacadeService(provider, FakeSearchProvider())

        service.loadResearchHome()
        service.loadResearchHome()

        assertThat(provider.latestCalls.get()).isEqualTo(1)
        assertThat(provider.rankingCalls.get()).isEqualTo(1)
    }

    @Test
    fun `종목 리서치는 검색 결과를 내부 ticker로 해석해서 조회한다`() = runBlocking {
        val provider = FakeResearchProvider()
        val service = ResearchFacadeService(provider, FakeSearchProvider())

        val result = service.loadStockResearch("삼성전자")

        assertThat(result.resolvedTicker).isNotNull
        assertThat(result.resolvedTicker?.symbol).isEqualTo("005930")
        assertThat(result.articles).hasSize(1)
        assertThat(provider.stockCalls.get()).isEqualTo(1)
    }

    private class FakeResearchProvider : ResearchProvider {
        val latestCalls = AtomicInteger()
        val rankingCalls = AtomicInteger()
        val stockCalls = AtomicInteger()

        override fun getCategoryLatestResearch(): Map<ResearchCategory, List<ResearchArticle>> {
            latestCalls.incrementAndGet()
            return mapOf(ResearchCategory.MARKET to listOf(article("core-1", "핵심 리서치")))
        }

        override fun getResearchRanking(rankingType: ResearchRankingType, selectedRank: Int): ResearchRankingBundle {
            rankingCalls.incrementAndGet()
            return ResearchRankingBundle(latestResearch = listOf(article("rank-1", "랭킹 리서치")))
        }

        override fun getStockResearch(itemCode: String, size: Int): List<ResearchArticle> {
            stockCalls.incrementAndGet()
            return listOf(article(itemCode, "${itemCode} 종목 리서치"))
        }

        private fun article(id: String, title: String): ResearchArticle {
            return ResearchArticle(
                researchId = id,
                title = title,
                itemCode = "005930",
                itemName = "삼성전자",
                brokerName = "미래에셋증권",
                writeDate = "2026-03-15"
            )
        }
    }

    private class FakeSearchProvider : SearchProvider {
        override fun search(query: String): List<Ticker> {
            return listOf(
                Ticker("005930", "005930", "삼성전자", MarketType.KOSPI, "KOR", "대한민국")
            )
        }
    }
}
