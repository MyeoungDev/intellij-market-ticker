package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.provider.ResearchProvider
import com.github.myeoungdev.marketticker.application.provider.ResearchLoadResult
import com.github.myeoungdev.marketticker.application.provider.ResearchLoadStatus
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
    fun `리서치 홈과 랭킹 상세는 서로 다른 캐시 값을 사용한다`() = runBlocking {
        val provider = FakeResearchProvider()
        val service = ResearchFacadeService(provider, FakeSearchProvider())

        service.loadResearchHome()
        service.loadRankingResearch(ResearchRankingType.SEARCH_TOP, 1)

        assertThat(provider.rankingCalls.get()).isEqualTo(2)
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

    @Test
    fun `리서치 실패는 FAILED 상태로 전달되고 다음 조회를 막지 않는다`() = runBlocking {
        val provider = FailingOnceResearchProvider()
        val service = ResearchFacadeService(provider, FakeSearchProvider())

        val failed = service.loadStockResearch("삼성전자")
        val recovered = service.loadStockResearch("삼성전자", forceRefresh = false)

        assertThat(failed.loadStatus).isEqualTo(ResearchLoadStatus.FAILED)
        assertThat(failed.statusMessage).isEqualTo("FAILED")
        assertThat(recovered.loadStatus).isEqualTo(ResearchLoadStatus.SUCCESS)
        assertThat(provider.stockCalls.get()).isEqualTo(2)
    }

    @Test
    fun `종목 리서치 요약은 실패와 빈 결과를 구분하고 실패를 캐시하지 않는다`() = runBlocking {
        val provider = FailingSummaryResearchProvider()
        val service = ResearchFacadeService(provider, FakeSearchProvider())
        val ticker = Ticker("005930", "005930", "삼성전자", MarketType.KOSPI, "KOR", "대한민국")

        val failed = service.loadTickerResearchSummary(ticker)
        val recovered = service.loadTickerResearchSummary(ticker)

        assertThat(failed.loadStatus).isEqualTo(ResearchLoadStatus.FAILED)
        assertThat(recovered.loadStatus).isEqualTo(ResearchLoadStatus.SUCCESS)
        assertThat(provider.summaryCalls.get()).isEqualTo(2)
        assertThat(provider.lastSummarySize).isEqualTo(3)
    }

    private open class FakeResearchProvider : ResearchProvider {
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

        protected fun article(id: String, title: String): ResearchArticle {
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

    private class FailingOnceResearchProvider : FakeResearchProvider() {
        override fun getStockResearchResult(itemCode: String, size: Int): ResearchLoadResult<List<ResearchArticle>> {
            return if (stockCalls.incrementAndGet() == 1) {
                ResearchLoadResult(emptyList(), ResearchLoadStatus.FAILED, "temporary upstream failure")
            } else {
                ResearchLoadResult(listOf(article(itemCode, "복구된 리서치")), ResearchLoadStatus.SUCCESS)
            }
        }
    }

    private class FailingSummaryResearchProvider : FakeResearchProvider() {
        val summaryCalls = AtomicInteger()
        var lastSummarySize: Int? = null

        override fun getStockResearchResult(itemCode: String, size: Int): ResearchLoadResult<List<ResearchArticle>> {
            summaryCalls.incrementAndGet()
            lastSummarySize = size
            return if (summaryCalls.get() == 1) {
                ResearchLoadResult(emptyList(), ResearchLoadStatus.FAILED, "temporary upstream failure")
            } else {
                ResearchLoadResult(listOf(article(itemCode, "복구된 요약")), ResearchLoadStatus.SUCCESS)
            }
        }
    }
}
